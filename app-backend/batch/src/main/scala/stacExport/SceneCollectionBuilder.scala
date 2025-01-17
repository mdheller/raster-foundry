package com.rasterfoundry.batch.stacExport

import geotrellis.vector.reproject.Reproject
import geotrellis.proj4.CRS
import com.rasterfoundry.datamodel._
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import geotrellis.server.stac._
import geotrellis.server.stac.{StacExtent => _}
import com.rasterfoundry.batch.stacExport.{StacExtent => BatchStacExtent}

object SceneCollectionBuilder {
  sealed trait CollectionRequirements
  object CollectionBuilder {
    trait EmptyCollection extends CollectionRequirements
    trait CollectionStacVersion extends CollectionRequirements
    trait CollectionId extends CollectionRequirements
    trait CollectionTitle extends CollectionRequirements
    trait CollectionExtent extends CollectionRequirements
    trait CollectionLinks extends CollectionRequirements
    trait CollectionDescription extends CollectionRequirements
    trait CollectionParentPath extends CollectionRequirements
    trait CollectionSceneList extends CollectionRequirements
    type CompleteCollection =
      EmptyCollection
        with CollectionStacVersion
        with CollectionId
        with CollectionTitle
        with CollectionExtent
        with CollectionLinks
        with CollectionDescription
        with CollectionParentPath
        with CollectionSceneList
  }
}

final case class IncompleteSceneCollection(
    stacVersion: Option[String] = None, // required
    id: Option[String] = None, // required
    title: Option[String] = None,
    description: Option[String] = None, // required
    keywords: Option[List[String]] = None,
    version: String = "1", // always 1, we aren't versioning exports
    license: Option[String] = None, // required
    providers: Option[List[StacProvider]] = None,
    extent: Option[BatchStacExtent] = None, // required
    properties: Option[Json] = None,
    links: List[StacLink] = List(), // builders?  // required
    parentPath: Option[String] = None,
    rootPath: Option[String] = None,
    sceneList: List[Scene] = List()
) {
  @SuppressWarnings(Array("OptionGet"))
  def toStacCollection: StacCollection = {
    val extent: Json = this.extent match {
      case Some(ext) => ext.asJson
      case None      => Json.Null
    }
    StacCollection(
      this.stacVersion.get,
      this.id.get,
      this.title,
      this.description.get,
      this.keywords.getOrElse(List()), // not required
      this.version,
      this.license.getOrElse(""), // required but not clear yet
      this.providers.getOrElse(List[StacProvider]()), // not required
      extent,
      JsonObject.empty, // properties, free-form json, not required
      this.links
    )
  }
}

class SceneCollectionBuilder[
    CollectionRequirements <: SceneCollectionBuilder.CollectionRequirements
](sceneCollection: IncompleteSceneCollection = IncompleteSceneCollection()) {
  import SceneCollectionBuilder.CollectionBuilder._

  def withVersion(
      version: String
  ): SceneCollectionBuilder[CollectionRequirements with CollectionStacVersion] =
    new SceneCollectionBuilder(
      sceneCollection.copy(stacVersion = Some(version)))

  def withId(
      id: String
  ): SceneCollectionBuilder[CollectionRequirements with CollectionId] =
    new SceneCollectionBuilder(sceneCollection.copy(id = Some(id)))

  def withTitle(
      title: String
  ): SceneCollectionBuilder[CollectionRequirements with CollectionTitle] =
    new SceneCollectionBuilder(sceneCollection.copy(title = Some(title)))

  def withDescription(
      description: String
  ): SceneCollectionBuilder[CollectionRequirements with CollectionDescription] =
    new SceneCollectionBuilder(
      sceneCollection.copy(description = Some(description))
    )

  def withLinks(
      links: List[StacLink]
  ): SceneCollectionBuilder[CollectionRequirements with CollectionLinks] =
    new SceneCollectionBuilder(
      sceneCollection.copy(links = sceneCollection.links ++ links)
    )

  def withExtent(
      extent: StacExtent
  ): SceneCollectionBuilder[CollectionRequirements with CollectionExtent] =
    new SceneCollectionBuilder(sceneCollection.copy(extent = Some(extent)))

  def withParentPath(
      parentPath: String,
      rootPath: String
  ): SceneCollectionBuilder[CollectionRequirements with CollectionParentPath] =
    new SceneCollectionBuilder(
      sceneCollection
        .copy(parentPath = Some(parentPath), rootPath = Some(rootPath))
    )

  def withSceneList(
      sceneList: List[Scene]
  ): SceneCollectionBuilder[CollectionRequirements with CollectionSceneList] =
    new SceneCollectionBuilder(
      sceneCollection
        .copy(sceneList = sceneCollection.sceneList ++ sceneList)
    )

  @SuppressWarnings(Array("OptionGet"))
  def build()(
      implicit ev: CollectionRequirements =:= CompleteCollection
  ): (StacCollection, List[StacItem], List[(String, String)]) = {
    ev.unused
    // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<sceneCollectionID>
    val absPath = sceneCollection.parentPath.get
    // ../../../catalog.json
    val rootPath = sceneCollection.rootPath.get

    val sceneItemsAndLinks: List[(StacItem, (String, String))] =
      sceneCollection.sceneList
        .map(scene => {
          val itemBuilder =
            new StacItemBuilder[StacItemBuilder.ItemBuilder.EmptyItem]()
          val sceneFootprint = Reproject(
            scene.dataFootprint.get.geom,
            CRS.fromEpsgCode(3857),
            CRS.fromEpsgCode(4326)
          )
          val sceneBbox = ItemBbox(
            sceneFootprint.envelope.xmin,
            sceneFootprint.envelope.ymin,
            sceneFootprint.envelope.xmax,
            sceneFootprint.envelope.ymax
          )
          // s3://rasterfoundry-production-data-us-east-1/stac-exports/<catalogId>/<layerCollectionId>/<sceneCollectionID>/<sceneId>
          val sceneAbsPath = s"${absPath}/${scene.id}"
          // ../../../../catalog.json
          val sceneRootPath = s"../${rootPath}"
          val itemLinkAndTitle: (String, String) =
            (s"${sceneAbsPath}/item.json", s"Scene Item ${scene.id.toString}")
          val sceneLinks = List(
            StacLink(
              itemLinkAndTitle._1,
              Self,
              Some(`application/json`),
              Some(itemLinkAndTitle._2)
            ),
            StacLink(
              s"${absPath}/collection.json",
              Parent,
              Some(`application/json`),
              Some("Scene Collection")
            ),
            StacLink(
              sceneRootPath,
              StacRoot,
              Some(`application/json`),
              Some("Root")
            )
          )
          val sceneProperties = JsonObject(
            (
              "datetime",
              parse(
                scene.filterFields.acquisitionDate.get.toLocalDateTime.toString
              ).getOrElse(Json.Null)
            )
          )
          val sceneAsset = Map(
            scene.id.toString ->
              StacAsset(
                scene.ingestLocation.get,
                Some("scene"),
                Some(`image/cog`)
              )
          )
          (
            itemBuilder
              .withId(scene.id.toString)
              .withGeometries(sceneFootprint, sceneBbox)
              .withLinks(sceneLinks)
              .withCollection(sceneCollection.id.get)
              .withProperties(sceneProperties)
              .withParentPath(absPath, rootPath)
              .withAssets(sceneAsset)
              .build(),
            itemLinkAndTitle
          )
        })

    val sceneLinks: List[(String, String)] = sceneItemsAndLinks.map(_._2)

    (
      sceneCollection
        .copy(
          links = sceneCollection.links ++ sceneLinks.map(link => {
            StacLink(
              link._1,
              Item,
              Some(`application/json`),
              Some(link._2)
            )
          })
        )
        .toStacCollection, // the scene collection
      sceneItemsAndLinks.map(_._1), // a list of scene items
      sceneLinks // a list of (sceneItemAbsLink, sceneItemTitle)
    )
  }
}
