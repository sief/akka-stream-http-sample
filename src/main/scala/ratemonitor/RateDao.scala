package ratemonitor

import slick.driver.H2Driver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object RateDao {

  // only one dao, so lets put the general db stuff here
  private lazy val db = Database.forConfig("h2mem")

  def setupDB: Future[Unit] = db.run(DBIO.seq(rates.schema.create))

  private val rates: TableQuery[Rates] = TableQuery[Rates]

  def insert(rate: Rate) = {
    db.run(rates += rate)
  }

  def insertIfDifferentToLast(rate: Rate) = {

    val query = for {
      lastSeq <- rates.sortBy(_.id.desc).take(1).result
      lastOption = lastSeq.headOption
      if lastOption.isEmpty || (lastOption.get.value != rate.value)
      res <- rates += rate
    } yield res

    // in this concrete example we don't have to expect a race condition, otherwise set appropriate isolation level
    db.run(query.transactionally)
  }

  def getStream = {
    db.stream(rates.sortBy(_.id.asc).result)
  }
}
