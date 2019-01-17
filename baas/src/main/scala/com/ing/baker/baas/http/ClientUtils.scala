package com.ing.baker.baas.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, PredefinedToEntityMarshallers}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{PredefinedFromEntityUnmarshallers, Unmarshaller}
import akka.serialization.{Serialization, SerializationExtension}
import akka.stream.Materializer
import akka.util.ByteString
import com.ing.baker.baas.protocol._
import com.ing.baker.il.CompiledRecipe
import com.ing.baker.runtime.core.{RuntimeEvent, SensoryEventStatus}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

trait ClientUtils {

  val actorSystem: ActorSystem
  val serializer: Serialization = SerializationExtension.get(actorSystem)

  val log = LoggerFactory.getLogger(this.getClass.getName)

  def entityFromResponse[T: ClassTag](entity: ResponseEntity)(implicit ct: ClassTag[T], materializer: Materializer, timeout: FiniteDuration): T = {
    val byteString = Await.result(entity.dataBytes.runFold(ByteString(""))(_ ++ _), timeout)
    serializer.deserialize(byteString.toArray, ct.runtimeClass).get.asInstanceOf[T]
  }

  def doRequestAndParseResponse[T: ClassTag](httpRequest: HttpRequest)(implicit actorSystem: ActorSystem, materializer: Materializer, timeout: FiniteDuration): T = {
    doRequest(httpRequest, e => entityFromResponse[T](e))
  }

  def doRequest[T](httpRequest: HttpRequest, fn: ResponseEntity => T)(implicit actorSystem: ActorSystem, materializer: Materializer, timeout: FiniteDuration): T = {

    log.info(s"Sending request: $httpRequest")

    val response: HttpMessage = Await.result(Http().singleRequest(httpRequest), timeout)

    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        fn(entity)
      case resp @ HttpResponse(code, _, _, _) =>
        resp.discardEntityBytes()
        log.error("Request failed with response code: " + code)
        throw new RuntimeException("Request failed with response code: " + code)
    }
  }

  def akkaProtoUnmarshaller[T: ClassTag](implicit ct: ClassTag[T]): Unmarshaller[HttpEntity, T] =
    PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller.map { string: Array[Byte] =>
      serializer.deserialize(string, ct.runtimeClass).get.asInstanceOf[T]
    }

  def akkaProtoMarshaller[T: ClassTag]: Marshaller[T, MessageEntity] = PredefinedToEntityMarshallers.ByteArrayMarshaller.compose[T] { obj =>
    serializer.serialize(obj.asInstanceOf[AnyRef]).get
  }

  implicit val addInteractionUnmarshaller = akkaProtoUnmarshaller[AddInteractionHTTPRequest]

  implicit val eventMarshaller = akkaProtoMarshaller[RuntimeEvent]
  implicit val eventUnmarshaller = akkaProtoUnmarshaller[RuntimeEvent]

  implicit val compiledRecipeMarshaller = akkaProtoMarshaller[CompiledRecipe]
  implicit val compiledRecipeUnmarshaller = akkaProtoUnmarshaller[CompiledRecipe]

  implicit val sensoryEventStatusMarhaller = akkaProtoMarshaller[SensoryEventStatus]
  implicit val sensoryEventStatusUnmarshaller = akkaProtoUnmarshaller[SensoryEventStatus]

  //TODO find out how to marshal these because we have no protobuff binding at this time
  implicit val eventListMarshaller = akkaProtoMarshaller[List[RuntimeEvent]]
  implicit val ingredientsMarhaller = akkaProtoMarshaller[Map[String, Any]]
}
