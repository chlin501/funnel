package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/

class InstanceSpec extends FlatSpec with Matchers {
  def inst(tags: (String,String)*): String =
    Instance("a", firewalls = "f1" :: Nil, tags =
      tags.toSeq.toMap
    ).application.get.toString


  it should "extract the right application when tags are present" in {
    inst(
      "type" -> "foo",
      "revision" -> "1.2.3",
      "aws:cloudformation:stack-name" -> "imdev-foo-1.2.3-Fsf42fx"
    ) should equal ( "foo-v1.2.3-Fsf42fx" )
  }

  it should "drop the qualifier if it is not present" in {
    inst(
      "type" -> "foo",
      "revision" -> "1.2.3"
    ) should equal ( "foo-v1.2.3" )

    // to support the old chef crap.
    inst(
      "Name" -> "service-imdev-contentkey-2-0-5",
      "aws:cloudformation:stack-name" -> "imdev-contentkey-2-0-5"
    ) should equal ( "service-imdev-contentkey-2-0-5-vunknown" )
  }

}