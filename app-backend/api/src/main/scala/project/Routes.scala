package com.rasterfoundry.api.project

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import better.files.{File => ScalaFile}
import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.api.scene._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.utils.Shapefile
import com.rasterfoundry.common.{AWSBatch, RollbarNotifier}
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel.{Annotation, _}
import com.rasterfoundry.akkautil.PaginationDirectives
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.shapefile.ShapeFileReader
import com.rasterfoundry.common.color._
import com.rasterfoundry.common._

trait ProjectRoutes
    extends Authentication
    with Config
    with Directives
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with ProjectSceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with RollbarNotifier
    with LazyLogging
    with ProjectAnnotationRoutes
    with ProjectLayerRoutes
    with ProjectLayerAnnotationRoutes
    with ProjectLayerTaskRoutes
    with ProjectAuthorizationDirectives {

  val xa: Transactor[IO]

  val projectRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        listProjects
      } ~
        post {
          createProject
        }
    } ~
      pathPrefix(JavaUUID) { projectId =>
        pathEndOrSingleSlash {
          get {
            getProject(projectId)
          } ~
            put {
              updateProject(projectId)
            } ~
            delete {
              deleteProject(projectId)
            }
        } ~
          pathPrefix("layers") {
            pathEndOrSingleSlash {
              post {
                createProjectLayer(projectId)
              } ~
                get {
                  listProjectLayers(projectId)
                }
            } ~ pathPrefix("stats") {
              pathEndOrSingleSlash {
                get {
                  getProjectLayerSceneCounts(projectId)
                }
              }
            } ~
              pathPrefix(JavaUUID) { layerId =>
                pathEndOrSingleSlash {
                  get {
                    getProjectLayer(projectId, layerId)
                  } ~
                    put {
                      updateProjectLayer(projectId, layerId)
                    } ~
                    delete {
                      deleteProjectLayer(projectId, layerId)
                    }
                } ~
                  pathPrefix("split") {
                    pathEndOrSingleSlash {
                      post {
                        splitProjectLayer(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("color-mode") {
                    pathEndOrSingleSlash {
                      post {
                        setProjectLayerColorMode(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("mosaic") {
                    pathEndOrSingleSlash {
                      get {
                        getProjectLayerMosaicDefinition(projectId, layerId)
                      }
                    } ~
                      pathPrefix(JavaUUID) { sceneId =>
                        pathEndOrSingleSlash {
                          get {
                            getProjectLayerSceneColorCorrectParams(projectId,
                                                                   layerId,
                                                                   sceneId)
                          } ~
                            put {
                              setProjectLayerSceneColorCorrectParams(projectId,
                                                                     layerId,
                                                                     sceneId)
                            }
                        }
                      } ~
                      pathPrefix("bulk-update-color-corrections") {
                        pathEndOrSingleSlash {
                          post {
                            setProjectLayerScenesColorCorrectParams(projectId,
                                                                    layerId)
                          }
                        }
                      }
                  } ~
                  pathPrefix("order") {
                    pathEndOrSingleSlash {
                      put {
                        setProjectLayerSceneOrder(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("labels") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerLabels(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("annotations") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerAnnotations(projectId, layerId)
                      } ~
                        post {
                          createLayerAnnotation(projectId, layerId)
                        } ~
                        delete {
                          deleteLayerAnnotations(projectId, layerId)
                        }
                    } ~
                      pathPrefix(JavaUUID) { annotationId =>
                        pathEndOrSingleSlash {
                          get {
                            getLayerAnnotation(projectId, annotationId, layerId)
                          } ~
                            put {
                              updateLayerAnnotation(projectId, layerId)
                            } ~
                            delete {
                              deleteLayerAnnotation(projectId,
                                                    annotationId,
                                                    layerId)
                            }
                        }
                      } ~
                      pathPrefix("shapefile") {
                        pathEndOrSingleSlash {
                          get {
                            exportLayerAnnotationShapefile(projectId, layerId)
                          } ~
                            post {
                              authenticate { _ =>
                                val tempFile = ScalaFile.newTemporaryFile()
                                tempFile.deleteOnExit()
                                val response =
                                  storeUploadedFile("name",
                                                    (_) => tempFile.toJava) {
                                    (_, _) =>
                                      processShapefile(projectId,
                                                       tempFile,
                                                       None,
                                                       Some(layerId))
                                  }
                                tempFile.delete()
                                response
                              }
                            }
                        } ~
                          pathPrefix("import") {
                            pathEndOrSingleSlash {
                              (post & formFieldMap) { fields =>
                                authenticate { _ =>
                                  val tempFile = ScalaFile.newTemporaryFile()
                                  tempFile.deleteOnExit()
                                  val response =
                                    storeUploadedFile("shapefile",
                                                      (_) => tempFile.toJava) {
                                      (_, _) =>
                                        processShapefile(projectId,
                                                         tempFile,
                                                         Some(fields),
                                                         Some(layerId))
                                    }
                                  tempFile.delete()
                                  response
                                }
                              }
                            }
                          }
                      }
                  } ~
                  pathPrefix("annotation-groups") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerAnnotationGroups(projectId, layerId)
                      } ~
                        post {
                          createLayerAnnotationGroup(projectId, layerId)
                        }
                    } ~
                      pathPrefix(JavaUUID) { annotationGroupId =>
                        pathEndOrSingleSlash {
                          get {
                            getLayerAnnotationGroup(projectId,
                                                    layerId,
                                                    annotationGroupId)
                          } ~
                            put {
                              updateLayerAnnotationGroup(projectId,
                                                         layerId,
                                                         annotationGroupId)
                            } ~
                            delete {
                              deleteLayerAnnotationGroup(projectId,
                                                         layerId,
                                                         annotationGroupId)
                            }
                        } ~
                          pathPrefix("summary") {
                            getLayerAnnotationGroupSummary(projectId,
                                                           layerId,
                                                           annotationGroupId)
                          }
                      }
                  } ~
                  pathPrefix("scenes") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerScenes(projectId, layerId)
                      } ~
                        post {
                          addProjectScenes(projectId, Some(layerId))
                        } ~
                        put {
                          updateProjectScenes(projectId, Some(layerId))
                        } ~
                        delete {
                          deleteProjectScenes(projectId, Some(layerId))
                        }
                    }
                  } ~
                  pathPrefix("datasources") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerDatasources(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("tasks") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerTasks(projectId, layerId)
                      } ~ post {
                        createLayerTask(projectId, layerId)
                      } ~ delete {
                        deleteLayerTasks(projectId, layerId)
                      }
                    } ~
                      pathPrefix("grid") {
                        post {
                          createLayerTaskGrid(projectId, layerId)
                        }
                      } ~
                      pathPrefix("summary") {
                        get {
                          getTaskUserSummary(projectId, layerId)
                        }
                      } ~
                      pathPrefix(JavaUUID) { taskId =>
                        pathEndOrSingleSlash {
                          get {
                            getTask(projectId, layerId, taskId)
                          } ~ put {
                            updateTask(projectId, layerId, taskId)
                          } ~ delete {
                            deleteTask(projectId, layerId, taskId)
                          }
                        } ~ pathPrefix("lock") {
                          pathEndOrSingleSlash {
                            post {
                              lockTask(projectId, layerId, taskId)
                            } ~ delete {
                              unlockTask(projectId, layerId, taskId)
                            }
                          }
                        }
                      }
                  }
              }
          } ~
          pathPrefix("project-color-mode") {
            pathEndOrSingleSlash {
              post {
                setProjectColorMode(projectId)
              }
            }
          } ~
          pathPrefix("labels") {
            pathEndOrSingleSlash {
              get {
                listLabels(projectId)
              }
            }
          } ~
          pathPrefix("annotation-groups") {
            pathEndOrSingleSlash {
              get {
                listAnnotationGroups(projectId)
              } ~
                post {
                  createAnnotationGroup(projectId)
                }
            } ~
              pathPrefix(JavaUUID) { annotationGroupId =>
                pathEndOrSingleSlash {
                  get {
                    getAnnotationGroup(projectId, annotationGroupId)
                  } ~
                    put {
                      updateAnnotationGroup(projectId, annotationGroupId)
                    } ~
                    delete {
                      deleteAnnotationGroup(projectId, annotationGroupId)
                    }
                } ~
                  pathPrefix("summary") {
                    getAnnotationGroupSummary(projectId, annotationGroupId)
                  }
              }
          } ~
          pathPrefix("annotations") {
            pathEndOrSingleSlash {
              get {
                listAnnotations(projectId)
              } ~
                post {
                  createAnnotation(projectId)
                } ~
                delete {
                  deleteProjectAnnotations(projectId)
                }
            } ~
              pathPrefix("shapefile") {
                pathEndOrSingleSlash {
                  get {
                    exportAnnotationShapefile(projectId)
                  } ~
                    post {
                      authenticate { _ =>
                        val tempFile = ScalaFile.newTemporaryFile()
                        tempFile.deleteOnExit()
                        val response =
                          storeUploadedFile("name", (_) => tempFile.toJava) {
                            (_, _) =>
                              processShapefile(projectId, tempFile, None, None)
                          }
                        tempFile.delete()
                        response
                      }
                    }
                } ~
                  pathPrefix("import") {
                    pathEndOrSingleSlash {
                      (post & formFieldMap) { fields =>
                        authenticate { _ =>
                          val tempFile = ScalaFile.newTemporaryFile()
                          tempFile.deleteOnExit()
                          val response =
                            storeUploadedFile("shapefile",
                                              (_) => tempFile.toJava) {
                              (_, _) =>
                                processShapefile(projectId,
                                                 tempFile,
                                                 Some(fields),
                                                 None)
                            }
                          tempFile.delete()
                          response
                        }
                      }
                    }
                  }
              } ~
              pathPrefix(JavaUUID) { annotationId =>
                pathEndOrSingleSlash {
                  get {
                    getAnnotation(projectId, annotationId)
                  } ~
                    put {
                      updateAnnotation(projectId)
                    } ~
                    delete {
                      deleteAnnotation(projectId, annotationId)
                    }
                }
              }
          } ~
          pathPrefix("areas-of-interest") {
            pathEndOrSingleSlash {
              get {
                listAOIs(projectId)
              } ~
                post {
                  createAOI(projectId)
                }
            }
          } ~
          pathPrefix("datasources") {
            pathEndOrSingleSlash {
              get {
                listProjectDatasources(projectId)
              }
            }
          } ~
          pathPrefix("scenes") {
            pathEndOrSingleSlash {
              get {
                listProjectScenes(projectId)
              } ~
                post {
                  addProjectScenes(projectId)
                } ~
                put {
                  updateProjectScenes(projectId)
                } ~
                delete {
                  deleteProjectScenes(projectId)
                }
            } ~
              pathPrefix("accept") {
                post {
                  acceptScenes(projectId)
                }
              } ~
              pathPrefix(JavaUUID) { sceneId =>
                pathPrefix("accept") {
                  post {
                    acceptScene(projectId, sceneId)
                  }
                }
              }
          } ~
          pathPrefix("mosaic") {
            pathEndOrSingleSlash {
              get {
                getProjectMosaicDefinition(projectId)
              }
            } ~
              pathPrefix(JavaUUID) { sceneId =>
                get {
                  getProjectSceneColorCorrectParams(projectId, sceneId)
                } ~
                  put {
                    setProjectSceneColorCorrectParams(projectId, sceneId)
                  }
              } ~
              pathPrefix("bulk-update-color-corrections") {
                pathEndOrSingleSlash {
                  post {
                    setProjectScenesColorCorrectParams(projectId)
                  }
                }
              }
          } ~
          pathPrefix("order") {
            pathEndOrSingleSlash {
              put {
                setProjectSceneOrder(projectId)
              }
            }
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceProjectPermissions(projectId)
              } ~
                post {
                  addProjectPermission(projectId)
                } ~
                get {
                  listProjectPermissions(projectId)
                } ~
                delete {
                  deleteProjectPermissions(projectId)
                }
            }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserProjectActions(projectId)
              }
            }
          }
      }
  }

  def listProjects: Route = authenticate { user =>
    (withPagination & projectQueryParameters) {
      (page, projectQueryParameters) =>
        complete {
          ProjectDao
            .listProjects(page, projectQueryParameters, user)
            .transact(xa)
            .unsafeToFuture
        }
    }
  }

  def createProject: Route = authenticate { user =>
    entity(as[Project.Create]) { newProject =>
      onSuccess(
        ProjectDao
          .insertProject(newProject, user)
          .transact(xa)
          .unsafeToFuture) { project =>
        complete(StatusCodes.Created, project)
      }
    }
  }

  def getProject(projectId: UUID): Route = extractTokenHeader { tokenO =>
    (extractMapTokenParam & projectQueryParameters) {
      (mapTokenO, projectQueryParams) =>
        (projectAuthFromMapTokenO(mapTokenO, projectId) |
          projectAuthFromTokenO(tokenO,
                                projectId,
                                projectQueryParams.analysisId) |
          projectIsPublic(projectId)) {
          complete {
            ProjectDao.query
              .filter(projectId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
          }
        }
    }
  }

  def updateProject(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Project]) { updatedProject =>
        onSuccess(
          ProjectDao
            .updateProject(updatedProject, projectId)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteProject(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(ProjectDao.deleteProject(projectId).transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def listLabels(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        AnnotationDao.listProjectLabels(projectId).transact(xa).unsafeToFuture
      }
    }
  }

  def listAOIs(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      withPagination { page =>
        complete {
          AoiDao.listAOIs(projectId, page).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createAOI(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[AOI.Create]) { aoi =>
        onSuccess(
          AoiDao
            .createAOI(aoi.toAOI(projectId, user), user: User)
            .transact(xa)
            .unsafeToFuture()) { a =>
          complete(StatusCodes.Created, a)
        }
      }
    }
  }

  def acceptScene(projectId: UUID, sceneId: UUID): Route = authenticate {
    user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          val acceptSceneIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            rowsAffected <- SceneToLayerDao.acceptScene(project.defaultLayerId,
                                                        sceneId)
          } yield { rowsAffected }

          acceptSceneIO.transact(xa).unsafeToFuture
        }
      }
  }

  def acceptScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[List[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }

        val acceptScenesIO = for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          rowsAffected <- SceneToLayerDao.acceptScenes(project.defaultLayerId,
                                                       sceneIds)
        } yield { rowsAffected }

        onSuccess(acceptScenesIO.transact(xa).unsafeToFuture) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  def listProjectScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      (withPagination & projectSceneQueryParameters) { (page, sceneParams) =>
        complete {
          val sceneListIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            scenes <- ProjectLayerScenesDao.listLayerScenes(
              project.defaultLayerId,
              page,
              sceneParams
            )
          } yield scenes
          sceneListIO.transact(xa).unsafeToFuture
        }
      }
    }
  }

  def listProjectDatasources(projectId: UUID): Route = authenticate { user =>
    (projectQueryParameters) { projectQueryParams =>
      authorizeAsync {
        val authorized = for {
          authProject <- ProjectDao.authorized(user,
                                               ObjectType.Project,
                                               projectId,
                                               ActionType.View)
          authResult <- (authProject, projectQueryParams.analysisId) match {
            case (AuthFailure(), Some(analysisId: UUID)) =>
              ToolRunDao
                .authorizeReferencedProject(user, analysisId, projectId)
            case (_, _) => Applicative[ConnectionIO].pure(authProject.toBoolean)
          }
        } yield authResult
        authorized.transact(xa).unsafeToFuture
      } {
        complete {
          val datasourcesIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            datasources <- ProjectLayerDatasourcesDao
              .listProjectLayerDatasources(project.defaultLayerId)
          } yield datasources
          datasourcesIO
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  /** Set the manually defined z-ordering for scenes within a given project */
  def setProjectSceneOrder(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Seq[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }

        val setOrderIO = for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          updatedOrder <- SceneToLayerDao.setManualOrder(projectId,
                                                         project.defaultLayerId,
                                                         sceneIds)
        } yield { updatedOrder }

        onSuccess(setOrderIO.transact(xa).unsafeToFuture) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  /** Get the color correction paramters for a project/scene pairing */
  def getProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) =
    authenticate { user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          val getColorCorrectParamsIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            params <- SceneToLayerDao.getColorCorrectParams(
              project.defaultLayerId,
              sceneId)
          } yield { params }

          getColorCorrectParamsIO.transact(xa).unsafeToFuture
        }
      }
    }

  /** Set color correction parameters for a project/scene pairing */
  def setProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) =
    authenticate { user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[ColorCorrect.Params]) { ccParams =>
          val setColorCorrectParamsIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            stl <- SceneToLayerDao.setColorCorrectParams(project.defaultLayerId,
                                                         sceneId,
                                                         ccParams)
          } yield { stl }

          onSuccess(setColorCorrectParamsIO.transact(xa).unsafeToFuture) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }

  def setProjectColorMode(projectId: UUID) = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[ProjectColorModeParams]) { colorBands =>
        val setProjectColorBandsIO = for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          rowsAffected <- SceneToLayerDao
            .setProjectLayerColorBands(project.defaultLayerId, colorBands)
        } yield { rowsAffected }

        onSuccess(setProjectColorBandsIO.transact(xa).unsafeToFuture) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  /** Set color correction parameters for a list of scenes */
  def setProjectScenesColorCorrectParams(projectId: UUID) = authenticate {
    user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[BatchParams]) { params =>
          val setColorCorrectParamsBatchIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            stl <- SceneToLayerDao
              .setColorCorrectParamsBatch(project.defaultLayerId, params)
          } yield { stl }

          onSuccess(setColorCorrectParamsBatchIO.transact(xa).unsafeToFuture) {
            _ =>
              complete(StatusCodes.NoContent)
          }
        }
      }
  }

  /** Get the information which defines mosaicing behavior for each scene in a given project */
  def getProjectMosaicDefinition(projectId: UUID) = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          val getMosaicDefinitionIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            result <- SceneToLayerDao
              .getMosaicDefinition(project.defaultLayerId)
              .compile
              .to[List]
          } yield { result }

          getMosaicDefinitionIO
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addProjectScenes(projectId: UUID, layerIdO: Option[UUID] = None): Route =
    authenticate { user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[NonEmptyList[UUID]]) { sceneIds =>
          if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
            complete(StatusCodes.RequestEntityTooLarge)
          }

          val scenesAdded = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            layerId = ProjectDao.getProjectLayerId(layerIdO, project)
            addedScenes <- ProjectDao.addScenesToProject(sceneIds,
                                                         projectId,
                                                         layerId,
                                                         true)
          } yield addedScenes

          complete { scenesAdded.transact(xa).unsafeToFuture }
        }
      }
    }

  def updateProjectScenes(projectId: UUID,
                          layerIdO: Option[UUID] = None): Route =
    authenticate { user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Seq[UUID]]) { sceneIds =>
          if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
            complete(StatusCodes.RequestEntityTooLarge)
          }

          sceneIds.toList.toNel match {
            case Some(ids) =>
              val replaceIO = for {
                project <- ProjectDao.unsafeGetProjectById(projectId)
                layerId = ProjectDao.getProjectLayerId(layerIdO, project)
                replacement <- ProjectDao.replaceScenesInProject(ids,
                                                                 projectId,
                                                                 layerId)
              } yield replacement

              complete {
                replaceIO.transact(xa).unsafeToFuture()
              }
            case _ => complete(StatusCodes.BadRequest)
          }
        }
      }
    }

  def deleteProjectScenes(projectId: UUID,
                          layerIdO: Option[UUID] = None): Route = authenticate {
    user =>
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Seq[UUID]]) { sceneIds =>
          if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
            complete(StatusCodes.RequestEntityTooLarge)
          }

          val deleteIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            layerId = ProjectDao.getProjectLayerId(layerIdO, project)
            deletion <- ProjectDao.deleteScenesFromProject(sceneIds.toList,
                                                           projectId,
                                                           layerId)
          } yield deletion
          onSuccess(deleteIO.transact(xa).unsafeToFuture) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }
  }

  def listProjectPermissions(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ProjectDao
          .getPermissions(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceProjectPermissions(projectId: UUID): Route = authenticate { user =>
    entity(as[List[ObjectAccessControlRule]]) { acrList =>
      authorizeAsync {
        (ProjectDao.authorized(user,
                               ObjectType.Project,
                               projectId,
                               ActionType.Edit) map { _.toBoolean },
         acrList traverse { acr =>
           ProjectDao.isValidPermission(acr, user)
         } map { _.foldLeft(true)(_ && _) }).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          ProjectDao
            .replacePermissions(projectId, acrList)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addProjectPermission(projectId: UUID): Route = authenticate { user =>
    entity(as[ObjectAccessControlRule]) { acr =>
      authorizeAsync {
        (ProjectDao.authorized(user,
                               ObjectType.Project,
                               projectId,
                               ActionType.Edit) map { _.toBoolean },
         ProjectDao.isValidPermission(acr, user)).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          ProjectDao
            .addPermission(projectId, acr)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listUserProjectActions(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      user.isSuperuser match {
        case true => complete(List("*"))
        case false =>
          onSuccess(
            ProjectDao
              .unsafeGetProjectById(projectId)
              .transact(xa)
              .unsafeToFuture
          ) { project =>
            project.owner == user.id match {
              case true => complete(List("*"))
              case false =>
                complete {
                  ProjectDao
                    .listUserActions(user, projectId)
                    .transact(xa)
                    .unsafeToFuture
                }
            }
          }
      }
    }
  }

  def deleteProjectPermissions(projectId: UUID): Route = authenticate { user =>
    authorizeAuthResultAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ProjectDao
          .deletePermissions(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def processShapefile(projectId: UUID,
                       tempFile: ScalaFile,
                       propsO: Option[Map[String, String]] = None,
                       projectLayerIdO: Option[UUID]): Route =
    authenticate { user =>
      {
        val unzipped = tempFile.unzip()
        val matches = unzipped.glob("*.shp")
        val prj = unzipped.glob("*.prj")
        (matches.hasNext, prj.hasNext) match {
          case (false, false) =>
            complete(
              StatusCodes.ClientError(400)(
                "Bad Request",
                "No .shp and .prj Files Found in Archive"))
          case (true, false) =>
            complete(
              StatusCodes.ClientError(400)("Bad Request",
                                           "No .prj File Found in Archive"))
          case (false, true) =>
            complete(
              StatusCodes.ClientError(400)("Bad Request",
                                           "No .shp File Found in Archive"))
          case (true, true) => {
            propsO match {
              case Some(props) =>
                processShapefileImport(matches,
                                       prj,
                                       props,
                                       user,
                                       projectId,
                                       projectLayerIdO)
              case _ =>
                complete(StatusCodes.OK, processShapefileUpload(matches))
            }
          }
        }
      }
    }

  def processShapefileImport(matches: Iterator[ScalaFile],
                             prj: Iterator[ScalaFile],
                             props: Map[String, String],
                             user: User,
                             projectId: UUID,
                             projectLayerIdO: Option[UUID]): Route = {
    val shapefilePath = matches.next.toString
    val prjPath: String = prj.next.toString
    val projectionSource = scala.io.Source.fromFile(prjPath)

    val features = ShapeFileReader.readSimpleFeatures(shapefilePath)
    val projection = try projectionSource.mkString
    finally projectionSource.close()

    val featureAccumulationResult =
      Shapefile.accumulateFeatures(Shapefile.fromSimpleFeatureWithProps)(
        List(),
        List(),
        features.toList,
        props,
        user.id,
        projection)
    featureAccumulationResult match {
      case Left(errorIndices) =>
        complete(
          StatusCodes.ClientError(400)(
            "Bad Request",
            s"Several features could not be translated to annotations. Indices: ${errorIndices}"
          )
        )
      case Right(annotationCreates) => {
        complete(
          StatusCodes.Created,
          (AnnotationDao.insertAnnotations(annotationCreates,
                                           projectId,
                                           user,
                                           projectLayerIdO)
            map { (anns: List[Annotation]) =>
              anns map { _.toGeoJSONFeature }
            }).transact(xa).unsafeToFuture
        )
      }
    }
  }

  def processShapefileUpload(matches: Iterator[ScalaFile]): List[String] = {
    // shapefile should have same fields in the property table
    // so it is fine to use toList(0)
    ShapeFileReader
      .readSimpleFeatures(matches.next.toString)
      .toList(0)
      .toString
      .split("SimpleFeatureImpl")
      .filter(s => s != "" && s.contains(".Attribute: "))
      .map(_.split(".Attribute: ")(1).split("<")(0))
      .toList
  }

}
