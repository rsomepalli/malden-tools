package org.lakumbra.mapzen

import com.typesafe.config.ConfigFactory
import org.apache.spark
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._

import org.elasticsearch.spark._

object Extractor {

  // scalastyle:off
  def main(args: Array[String]):Unit = {
    val csvio = new SparkCSVIO
    val config = ConfigFactory.load()
    val ss = SparkSession.builder()
      .config("es.index.auto.create", "true")
        .config("es.nodes", config.getString("lakumbra.es.nodes"))
        .config("es.cluster.name" ,config.getString("lakumbra.es.cluster"))
      .appName("MapZenLocationIndexer")
      .master("local")
      .getOrCreate()

    val baseFolder = config.getString("lakumbra.input-files.folder")
    implicit val sqlContext = ss.sqlContext
    csvio.loadCSVDataFrame(s"${baseFolder}/wof-continent-latest.csv")
      .select("id", "name", "geom_latitude", "geom_longitude" )
      .createOrReplaceTempView("continents")

    csvio.loadCSVDataFrame(s"$baseFolder/wof-country-latest.csv")
      .select("id", "name", "geom_latitude", "geom_longitude" , "parent_id", "iso_country")
      .createOrReplaceTempView("countries")

    csvio.loadCSVDataFrame(s"$baseFolder/wof-region-latest.csv")
      .select("id", "name", "geom_latitude", "geom_longitude" , "country_id")
      .createOrReplaceTempView("regions")

    csvio.loadCSVDataFrame(s"$baseFolder/wof-locality-latest.csv")
      .select("id", "name", "geom_latitude", "geom_longitude", "region_id", "country_id" )
      .createOrReplaceTempView("locations")

    val locRegCouConDF = sqlContext.sql(
      """
        |SELECT con.id as con_id,
        | con.name con_name,
        | con.geom_latitude as con_latitude,
        | con.geom_longitude as con_longitude,
        | cou.id as cou_id,
        | cou.name as cou_name,
        | cou.geom_latitude as cou_latitude,
        | cou.geom_longitude as cou_longitude,
        | loc.region_id as reg_id,
        | reg.name as reg_name,
        | reg.geom_latitude as reg_latitude,
        | reg.geom_longitude as reg_longitude,
        | loc.id as loc_id,
        | loc.name as loc_name,
        | loc.geom_latitude as loc_latitude,
        | loc.geom_longitude as loc_longitude
        |FROM
        | locations loc left join regions reg on loc.region_id=reg.id
        | join countries cou on loc.country_id=cou.id
        | JOIN continents con on cou.parent_id=con.id
      """.stripMargin)
          .repartition(10)
    csvio.writeDataFrame(s"$baseFolder/output", locRegCouConDF.repartition(1))
//    import ss.implicits._
//    locRegCouConDF.map(r=>LocationDoc(
//      r.getAs[Int]("con_id"),
//      r.getAs[String]("con_name"),
//      Location(r.getAs[Double]("con_latitude"), r.getAs[Double]("con_longitude")),
//      r.getAs[Int]("cou_id"),
//      r.getAs[String]("cou_name"),
//      Location(r.getAs[Double]("cou_latitude"), r.getAs[Double]("cou_longitude")),
//      r.getAs[Int]("reg_id"),
//      r.getAs[String]("reg_name"),
//      Location(r.getAs[Double]("reg_latitude"), r.getAs[Double]("reg_longitude")),
//      r.getAs[Int]("loc_id"),
//      r.getAs[String]("loc_name"),
//      Location(r.getAs[Double]("loc_latitude"), r.getAs[Double]("loc_longitude"))
//    )).write.mode(SaveMode.Append)
//    .format("org.elasticsearch.spark.sql")
//    .option("es.resource","mapzen_locations/location")
//    .save()

    ss.stop()
  }

  case class Location(lat:Double, lon: Double)
  case class LocationDoc(con_id: Int, con_name: String, con_lat_lon: Location,
                         cou_id: Int, cou_name: String, cou_lat_lon: Location,
                         reg_id: Int, reg_name: String, reg_lat_lon: Location,
                         loc_id: Int, loc_name: String, loc_lat_lon: Location
                        )
}


/**
{
  "mappings": {
    "location": {
      "properties": {
        "con_id": {
          "type": "long"
        },
        "con_name": {
          "type": "string",
          "index": "not_analyzed"
        },
        "con_lat_lon": {
          "type": "geo_point"
        },
        "cou_id": {
          "type": "long"
        },
        "cou_name": {
          "type": "string",
          "index": "not_analyzed"
        },
        "cou_lat_lon": {
          "type": "geo_point"
        },
        "reg_id": {
          "type": "long"
        },
        "reg_name": {
          "type": "string",
          "index": "not_analyzed"
        },
        "reg_lat_lon": {
          "type": "geo_point"
        },
        "loc_id": {
          "type": "long"
        },
        "loc_name": {
          "type": "string",
          "index": "not_analyzed"
        },
        "loc_lat_lon": {
          "type": "geo_point"
        }
      }
    }
  }
}
  */