package org.w3.vs

import org.w3.vs.model._

package object model {

  type AssertorsConfiguration = Map[AssertorId, AssertorConfiguration]

  type AssertorConfiguration = Map[String, List[String]]

  object AssertorsConfiguration {
    import org.w3.vs.assertor._
    val default: AssertorsConfiguration =
      Map(
        CSSValidator.id -> Map.empty,
        HTMLValidator.id -> Map.empty,
        I18nChecker.id -> Map.empty)
  }

}