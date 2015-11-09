val k ="""{"total_rows":11900,"offset":0,"rows":[
  {"id":"a:5668887885","key":1442232280,"value":["http://allegro.pl/show_item.php?item=5668887885","1-33c580cec18fbe6f122e95a7e2bd9cbc"]}
  ]}"""
val v ="""{"total_rows":11900,"offset":0,"rows":[
  ]}"""
import spray.json._
object CouchResponses extends DefaultJsonProtocol {
  type URV = (String,String)
  case class Rows[T](id:String, key:JsValue,value:T)

  implicit val unprocessedRowsResponse = jsonFormat3(Rows[URV])
  case class ViewResponse[T](total_rows: Int, offset: Int, rows:List[Rows[T]])
  implicit val unprocessedViewResponse = jsonFormat3(ViewResponse[URV])
}
import CouchResponses._
v.parseJson.convertTo[ViewResponse[URV]]
//import scala.util.Try
/*Try(k.parseJson.asJsObject)
.flatMap(x => Try(x.fields("rows")))
.map()
  //.map()
  */
