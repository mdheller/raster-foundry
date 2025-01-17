import scala.util.Properties

import sbt._

object Version {
  val akka = "2.5.11"
  val akkaCirceJson = "1.22.0"
  val akkaHttp = "10.0.11"
  val akkaHttpCors = "0.2.2"
  val akkaSlf4j = "2.4.13"
  val apacheCommonsEmail = "1.5"
  val auth0 = "1.5.0"
  val awsBatchSdk = "1.11.535"
  val awsLambdaCore = "1.1.0"
  val awsLambdaSdk = "1.11.535"
  val awsS3 = "1.11.535"
  val awsStsSdk = "1.11.535"
  val betterFiles = "3.4.0"
  val caffeine = "2.3.5"
  val cats = "1.6.0"
  val catsEffect = "1.0.0"
  val catsMeow = "0.2.0"
  val catsScalacheck = "0.1.1"
  val circe = "0.11.1"
  val circeOptics = "0.11.0"
  val commonsIO = "2.5"
  val dnsJava = "2.1.8"
  val doobie = "0.7.0"
  val dropbox = "3.0.9"
  val elasticacheClient = "1.1.1"
  val ficus = "1.4.0"
  val flyway = "5.2.4"
  val geotools = "17.1"
  val geotrellisContrib = "3.16.1-M3"
  val geotrellis = "3.0.0-M3"
  val geotrellisServer = "3.4.0"
  val hadoop = "2.8.4"
  val http4s = "0.20.0"
  val json4s = "3.5.0"
  val jts = "1.16.0"
  val logback = "1.2.3"
  val maml = "0.4.0"
  val nimbusJose = "0.6.0"
  val postgis = "2.2.1"
  val rollbar = "1.4.0"
  val scaffeine = "2.0.0"
  val scala = "2.12.8"
  val scalaCheck = "1.14.0"
  val scalacache = "0.27.0"
  val scalaLogging = "3.9.0"
  val scalaTest = "3.0.1"
  val scalajHttp = "2.3.0"
  val scapegoat = "1.3.7"
  val scopt = "3.5.0"
  val decline = "0.6.0"
  val slf4j = "1.7.25"
  val spatial4j = "0.7"
  val spark = "2.4.2"
  val spire = "0.16.0"
  val spray = "1.3.4"
  val sup = "0.2.0"
}

object Dependencies {
  val akkaCirceJson = "de.heikoseeberger" %% "akka-http-circe" % Version.akkaCirceJson
  val akkaHttpCors = "ch.megard" %% "akka-http-cors" % Version.akkaHttpCors
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.akkaSlf4j % Runtime
  val akkahttp = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
  val akkastream = "com.typesafe.akka" %% "akka-stream" % Version.akka
  val akkatestkit = "com.typesafe.akka" %% "akka-http-testkit" % Version.akkaHttp
  val apacheCommonsEmail = "org.apache.commons" % "commons-email" % Version.apacheCommonsEmail
  val auth0 = "com.auth0" % "auth0" % Version.auth0
  val awsBatchSdk = "com.amazonaws" % "aws-java-sdk-batch" % Version.awsBatchSdk
  val awsLambdaCore = "com.amazonaws" % "aws-lambda-java-core" % Version.awsLambdaCore
  val awsLambdaSdk = "com.amazonaws" % "aws-java-sdk-lambda" % Version.awsLambdaSdk
  val awsS3 = "com.amazonaws" % "aws-java-sdk-s3" % Version.awsS3
  val awsStsSdk = "com.amazonaws" % "aws-java-sdk-sts" % Version.awsStsSdk
  val betterFiles = "com.github.pathikrit" %% "better-files" % Version.betterFiles
  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % Version.caffeine
  val catsCore = "org.typelevel" %% "cats-core" % Version.cats
  val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect
  val catsMeow = "com.olegpy" %% "meow-mtl" % Version.catsMeow
  val catsScalacheck = "io.chrisdavenport" %% "cats-scalacheck" % Version.catsScalacheck % "test"
  val circeCore = "io.circe" %% "circe-core" % Version.circe
  val circeGeneric = "io.circe" %% "circe-generic" % Version.circe
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % Version.circe
  val circeOptics = "io.circe" %% "circe-optics" % Version.circeOptics
  val circeParser = "io.circe" %% "circe-parser" % Version.circe
  val circeTest = "io.circe" %% "circe-testing" % Version.circe % "test"
  val clistCore = "org.backuity.clist" %% "clist-core" % "3.5.0"
  val clistMacros = "org.backuity.clist" %% "clist-macros" % "3.5.0"
  val commonsIO = "commons-io" % "commons-io" % Version.commonsIO
  val doobieCore = "org.tpolecat" %% "doobie-core" % Version.doobie
  val doobieHikari = "org.tpolecat" %% "doobie-hikari" % Version.doobie
  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % Version.doobie
  val doobiePostgresCirce = "org.tpolecat" %% "doobie-postgres-circe" % Version.doobie
  val doobieScalatest = "org.tpolecat" %% "doobie-scalatest" % Version.doobie
  val doobieSpecs = "org.tpolecat" %% "doobie-specs2" % Version.doobie
  val dropbox = "com.dropbox.core" % "dropbox-core-sdk" % Version.dropbox
  val elasticacheClient = "com.amazonaws" % "elasticache-java-cluster-client" % Version.elasticacheClient
  val ficus = "com.iheart" %% "ficus" % Version.ficus
  val flyway = "org.flywaydb" % "flyway-core" % Version.flyway
  val geotrellisContribVLM = "com.azavea.geotrellis" %% "geotrellis-contrib-vlm" % Version.geotrellisContrib
  val geotrellisContribGDAL = "com.azavea.geotrellis" %% "geotrellis-contrib-gdal" % Version.geotrellisContrib
  val geotrellisGeotools = "org.locationtech.geotrellis" %% "geotrellis-geotools" % Version.geotrellis
  val geotrellisRaster = "org.locationtech.geotrellis" %% "geotrellis-raster" % Version.geotrellis
  val geotrellisS3 = "org.locationtech.geotrellis" %% "geotrellis-s3" % Version.geotrellis
  val geotrellisServer = "com.azavea" %% "geotrellis-server-core" % Version.geotrellisServer
  val geotrellisProj4 = "org.locationtech.geotrellis" %% "geotrellis-proj4" % Version.geotrellis
  val geotrellisServerOgc = "com.azavea" %% "geotrellis-server-ogc" % Version.geotrellisServer
  val geotrellisServerStac = "com.azavea" %% "geotrellis-server-stac" % Version.geotrellisServer
  val geotrellisShapefile = "org.locationtech.geotrellis" %% "geotrellis-shapefile" % Version.geotrellis
  val geotrellisSpark = "org.locationtech.geotrellis" %% "geotrellis-spark" % Version.geotrellis
  val geotrellisUtil = "org.locationtech.geotrellis" %% "geotrellis-util" % Version.geotrellis
  val geotrellisVector = "org.locationtech.geotrellis" %% "geotrellis-vector" % Version.geotrellis
  val geotrellisVectorTestkit = "org.locationtech.geotrellis" %% "geotrellis-vector-testkit" % Version.geotrellis % "test"
  val http4sBlaze = "org.http4s" %% "http4s-blaze-server" % Version.http4s
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % Version.http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % Version.http4s
  val http4sDSL = "org.http4s" %% "http4s-dsl" % Version.http4s
  val http4sServer = "org.http4s" %% "http4s-server" % Version.http4s
  val http4sXml = "org.http4s" %% "http4s-scala-xml" % Version.http4s
  val logbackClassic = "ch.qos.logback" % "logback-classic" % Version.logback
  val mamlJvm = "com.azavea.geotrellis" %% "maml-jvm" % Version.maml
  val monocleCore = "com.github.julien-truffaut" %% "monocle-core" % "1.5.1-cats"
  val nimbusJose = "com.guizmaii" %% "scala-nimbus-jose-jwt" % Version.nimbusJose
  val postgis = "net.postgis" % "postgis-jdbc" % Version.postgis
  val rollbar = "com.rollbar" % "rollbar-java" % Version.rollbar
  val scaffeine = "com.github.blemale" %% "scaffeine" % Version.scaffeine
  val scalacacheCore = "com.github.cb372" %% "scalacache-core" % Version.scalacache
  val scalacacheCats = "com.github.cb372" %% "scalacache-cats-effect" % Version.scalacache
  val scalacacheCaffeine = "com.github.cb372" %% "scalacache-caffeine" % Version.scalacache
  val scalacacheMemcached = "com.github.cb372" %% "scalacache-memcached" % Version.scalacache intransitive ()
  val scalaCheck = "org.scalacheck" %% "scalacheck" % Version.scalaCheck % "test"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % Version.scalaLogging
  val scalatest = "org.scalatest" %% "scalatest" % Version.scalaTest % "test"
  val scopt = "com.github.scopt" %% "scopt" % Version.scopt
  val shapeless = "com.chuusai" %% "shapeless" % "2.3.3"
  val log4jOverslf4j = "org.slf4j" % "slf4j-simple" % Version.slf4j
  val slf4j = "org.slf4j" % "slf4j-api" % Version.slf4j
  val sttpCore = "com.softwaremill.sttp" %% "core" % "1.5.12"
  val sttpJson = "com.softwaremill.sttp" %% "json-common" % "1.5.12"
  val sttpCirce = "com.softwaremill.sttp" %% "circe" % "1.5.12"
  val spire = "org.typelevel" %% "spire" % Version.spire
  val decline = "com.monovore" %% "decline" % Version.decline
  val spark = "org.apache.spark" %% "spark-core" % Version.spark % "provided"
  val sparkCore = "org.apache.spark" %% "spark-core" % Version.spark
  val spatial4j = "org.locationtech.spatial4j" % "spatial4j" % Version.spatial4j
  val spray = "io.spray" %% "spray-json" % Version.spray
  val sup = "com.kubukoz" %% "sup-core" % Version.sup
}
