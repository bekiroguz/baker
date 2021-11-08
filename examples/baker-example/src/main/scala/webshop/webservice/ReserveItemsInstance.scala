package webshop.webservice

import cats.effect.{IO, Timer}
import webshop.webservice.CheckoutFlowEvents.ReserveItemsOutput
import webshop.webservice.CheckoutFlowIngredients.{Item, ReservedItems}
import webshop.webservice.CheckoutFlowInteractions.ReserveItems

import scala.concurrent.Future
import scala.concurrent.duration._

class ReserveItemsInstance(implicit timer: Timer[IO]) extends ReserveItems {

  override def apply(items: List[Item]): Future[ReserveItemsOutput] = {
      IO.sleep(1.second)
        .as(CheckoutFlowEvents.ItemsReserved(ReservedItems(items, Array.fill(1000)(Byte.MaxValue))))
        .unsafeToFuture()
  }
}
