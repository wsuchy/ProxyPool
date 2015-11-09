package xyz.suchanek.parsers

import org.htmlcleaner.{HtmlCleaner, TagNode}

import scala.util.Try

object Xroxy {
  def getProxyList(data:String) = {
    val content = (new HtmlCleaner()).clean(data)
    val l1 = content.evaluateXPath("//tr[@class='row0']").toList
    val l2 = content.evaluateXPath("//tr[@class='row1']").toList
    val total = l1 ++ l2

    Try(
      total
        .map(_.asInstanceOf[TagNode])
        .map(_.getAllElements(false))
        .filter(_.length > 3)
        .map(x => (x.apply(1).getText.toString.trim(), x.apply(2).getText.toString.toInt))
    ).toOption

  }
}
