package org.w3.vs.actor

import JobActor._
import akka.actor._
import akka.dispatch._
import play.Logger
import com.typesafe.config.Config

class JobActorMailbox(settings: ActorSystem.Settings, config: Config)
extends UnboundedPriorityMailbox(
  PriorityGenerator {
    case GetRun => 0
    case PoisonPill => 3
    case otherwize => 5
  }
)