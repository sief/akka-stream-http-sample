package ratemonitor

import java.sql.Timestamp

import slick.driver.H2Driver.api._

case class Rate(timestamp: Timestamp, value: BigDecimal, id: Option[Long] = None)

class Rates(tag: Tag) extends Table[Rate](tag, "RATES") {

  def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

  def timestamp = column[Timestamp]("TIMESTAMP")

  def value = column[BigDecimal]("VALUE")

  def * = (timestamp, value, id.?) <>(Rate.tupled, Rate.unapply)
}
