// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.rogue

import com.mongodb.DBObject
import net.liftweb.json.{Extraction, Formats, Serializer, TypeInfo}
import net.liftweb.json.JsonAST.{JObject, JValue}
import net.liftweb.mongodb._
import net.liftweb.mongodb.record._

case class Degrees(value: Double)
case class LatLong(lat: Double, long: Double)

object QueryHelpers {
  class DBObjectSerializer extends Serializer[DBObject] {
    val DBObjectClass = classOf[DBObject]

    def deserialize(implicit formats: Formats): PartialFunction[(TypeInfo, JValue), DBObject] = {
      case (TypeInfo(klass, _), json : JObject) if DBObjectClass.isAssignableFrom(klass) =>
        JObjectParser.parse(json)
    }

    def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = {
      case x: DBObject =>
        JObjectParser.serialize(x)
    }
  }

  private implicit val formats =
    (net.liftweb.json.DefaultFormats + new ObjectIdSerializer + new DBObjectSerializer)

  trait QueryLogger {
    def log(msg: => String, timeMillis: Long): Unit
    def warn(msg: => String): Unit
  }

  object NoopQueryLogger extends QueryLogger {
    override def log(msg: => String, timeMillis: Long) {}
    override def warn(msg: => String) {}
  }

  var logger: QueryLogger = NoopQueryLogger

  trait QueryValidator {
    def validateList[T](xs: Iterable[T]): Unit
    def validateRadius(d: Degrees): Degrees
    def validateQuery[M <: MongoRecord[M]](query: BaseQuery[M, _, _, _, _, _]): Unit
    def validateModify[M <: MongoRecord[M]](modify: ModifyQuery[M]): Unit
  }

  object NoopQueryValidator extends QueryValidator {
    override def validateList[T](xs: Iterable[T]) {}
    override def validateRadius(d: Degrees) = d
    override def validateQuery[M <: MongoRecord[M]](query: BaseQuery[M, _, _, _, _, _]) {}
    override def validateModify[M <: MongoRecord[M]](modify: ModifyQuery[M]) {}
  }

  var validator: QueryValidator = NoopQueryValidator

  def makeJavaList[T](sl: Iterable[T]): java.util.List[T] = {
    val list = new java.util.ArrayList[T]()
    for (id <- sl) list.add(id)
    list
  }

  def list[T](vs: Iterable[T]): java.util.List[T] = {
    validator.validateList(vs)
    makeJavaList(vs)
  }

  def list(vs: Double*): java.util.List[Double] = list(vs)

  def radius(d: Degrees) = {
    validator.validateRadius(d).value
  }

  def makeJavaMap[K, V](m: Map[K, V]): java.util.Map[K, V] = {
    val map = new java.util.HashMap[K, V]
    for ((k, v) <- m) map.put(k, v)
    map
  }

  def inListClause[V](fieldName: String, vs: Iterable[V]) = {
    if (vs.isEmpty)
      new EmptyQueryClause[java.util.List[V]](fieldName)
    else
      new QueryClause(fieldName, CondOps.In -> QueryHelpers.list(vs))
  }

  def allListClause[V](fieldName: String, vs: Iterable[V]) = {
    if (vs.isEmpty)
      new EmptyQueryClause[java.util.List[V]](fieldName)
    else
      new QueryClause(fieldName, CondOps.All -> QueryHelpers.list(vs))
  }

  def asDBObject[T](x: T): DBObject = {
    JObjectParser.parse(Extraction.decompose(x).asInstanceOf[JObject])
  }
}
