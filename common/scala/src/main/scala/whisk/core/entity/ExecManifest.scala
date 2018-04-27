/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.entity

import pureconfig.loadConfigOrThrow

import scala.util.{Failure, Try}
import spray.json._
import spray.json.DefaultJsonProtocol._
import whisk.core.{ConfigKeys, WhiskConfig}
import whisk.core.entity.Attachments._
import whisk.core.entity.Attachments.Attached._
import whisk.core.entity.size._

/**
 * Reads manifest of supported runtimes from configuration file and stores
 * a representation of each runtime which is used for serialization and
 * deserialization. This singleton must be initialized.
 */
protected[core] object ExecManifest {

  /**
   * Required properties to initialize this singleton via WhiskConfig.
   */
  protected[core] def requiredProperties = Map(WhiskConfig.runtimesManifest -> null)

  /**
   * Reads runtimes manifest from WhiskConfig and initializes the
   * singleton Runtime instance.
   *
   * @param config a valid configuration
   * @return the manifest if initialized successfully, or an failure
   */
  protected[core] def initialize(config: WhiskConfig): Try[Runtimes] = {
    val rmc = loadConfigOrThrow[RuntimeManifestConfig](ConfigKeys.runtimes)
    val mf = Try(config.runtimesManifest.parseJson.asJsObject).flatMap(runtimes(_, rmc))
    mf.foreach(m => manifest = Some(m))
    mf
  }

  /**
   * Gets existing runtime manifests.
   *
   * @return singleton Runtimes instance previous initialized from WhiskConfig
   * @throws IllegalStateException if singleton was not previously initialized
   */
  @throws[IllegalStateException]
  protected[core] def runtimesManifest: Runtimes = {
    manifest.getOrElse {
      throw new IllegalStateException("Runtimes manifest is not initialized.")
    }
  }

  private var manifest: Option[Runtimes] = None

  /**
   * @param config a configuration object as JSON
   * @return Runtimes instance
   */
  protected[entity] def runtimes(config: JsObject, runtimeManifestConfig: RuntimeManifestConfig): Try[Runtimes] = Try {

    val prefix = runtimeManifestConfig.defaultImagePrefix
    val tag = runtimeManifestConfig.defaultImageTag

    var prewarmingConfigs: Set[PrewarmingConfig] = Set.empty

    val runtimes = config.fields
      .get("runtimes")
      .map(_.convertTo[Map[String, Set[RuntimeManifest]]].map {
        case (name, versions) =>
          RuntimeFamily(name, versions.map { mf =>
            val img = ImageName(mf.image.name, mf.image.prefix.orElse(prefix), mf.image.tag.orElse(tag))
            val new_mf = mf.copy(image = img)
            mf.stemCells.foreach(_.foreach { stemCell =>
              if(stemCell.contains("count") && stemCell.contains("memory"))
                prewarmingConfigs += PrewarmingConfig(stemCell("count"),
                                                      CodeExecAsString(new_mf, "", None),
                                                      stemCell("memory").MB)
            })
            //keep stemCells in manifest will disturb the test codes, so just set it to None
            new_mf.copy(stemCells = None)
          })
      }.toSet)

    val blackbox = config.fields
      .get("blackboxes")
      .map(_.convertTo[Set[ImageName]].map { image =>
        ImageName(image.name, image.prefix.orElse(prefix), image.tag.orElse(tag))
      })

    val bypassPullForLocalImages = runtimeManifestConfig.bypassPullForLocalImages
      .filter(identity)
      .flatMap(_ => runtimeManifestConfig.localImagePrefix)

    Runtimes(runtimes.getOrElse(Set.empty), blackbox.getOrElse(Set.empty), prewarmingConfigs, bypassPullForLocalImages)
  }

  /**
   * The config for prewarmed containers
   *
   * @param count count of this kind of prewarmed containers
   * @param exec specify the metadata of a container
   * @param memoryLimit memory limit of this kind of container
   */
  protected[core] case class PrewarmingConfig(count: Int, exec: CodeExec[_], memoryLimit: ByteSize)

  /**
   * Misc options related to runtime manifests
   * @param defaultImagePrefix the default image prefix when not given explicitly
   * @param defaultImageTag the default image tag
   * @param bypassPullForLocalImages if true, allow images with a prefix that matches localImagePrefix
   *                                 to skip docker pull in invoker even if the image is not part of the blackbox set
   * @param localImagePrefix image prefix for bypassPullForLocalImages
   */
  protected[core] case class RuntimeManifestConfig(defaultImagePrefix: Option[String] = None,
                                                   defaultImageTag: Option[String] = None,
                                                   bypassPullForLocalImages: Option[Boolean] = None,
                                                   localImagePrefix: Option[String] = None)

  /**
   * A runtime manifest describes the "exec" runtime support.
   *
   * @param kind the name of the kind e.g., nodejs:6
   * @param deprecated true iff the runtime is deprecated (allows get/delete but not create/update/invoke)
   * @param default true iff the runtime is the default kind for its family (nodejs:default -> nodejs:6)
   * @param attached true iff the source is an attachments (not inlined source)
   * @param requireMain true iff main entry point is not optional
   * @param sentinelledLogs true iff the runtime generates stdout/stderr log sentinels after an activation
   * @param image optional image name, otherwise inferred via fixed mapping (remove colons and append 'action')
   */
  protected[core] case class RuntimeManifest(kind: String,
                                             image: ImageName,
                                             deprecated: Option[Boolean] = None,
                                             default: Option[Boolean] = None,
                                             attached: Option[Attached] = None,
                                             requireMain: Option[Boolean] = None,
                                             sentinelledLogs: Option[Boolean] = None,
                                             stemCells: Option[List[Map[String, Int]]] = None) {

    protected[entity] def toJsonSummary = {
      JsObject(
        "kind" -> kind.toJson,
        "image" -> image.publicImageName.toJson,
        "deprecated" -> deprecated.getOrElse(false).toJson,
        "default" -> default.getOrElse(false).toJson,
        "attached" -> attached.isDefined.toJson,
        "requireMain" -> requireMain.getOrElse(false).toJson)
    }
  }

  /**
   * An image name for an action refers to the container image canonically as
   * "prefix/name[:tag]" e.g., "openwhisk/python3action:latest".
   */
  protected[core] case class ImageName(name: String, prefix: Option[String] = None, tag: Option[String] = None) {

    /**
     * The name of the public image for an action kind.
     */
    def publicImageName: String = {
      val p = prefix.filter(_.nonEmpty).map(_ + "/").getOrElse("")
      val t = tag.filter(_.nonEmpty).map(":" + _).getOrElse("")
      p + name + t
    }

    /**
     * The internal name of the image for an action kind. It overrides
     * the prefix with an internal name. Optionally overrides tag.
     */
    def localImageName(registry: String, prefix: String, tagOverride: Option[String] = None): String = {
      val r = Option(registry)
        .filter(_.nonEmpty)
        .map { reg =>
          if (reg.endsWith("/")) reg else reg + "/"
        }
        .getOrElse("")
      val p = Option(prefix).filter(_.nonEmpty).map(_ + "/").getOrElse("")
      r + p + name + ":" + tagOverride.orElse(tag).getOrElse(ImageName.defaultImageTag)
    }

    /**
     * Overrides equals to allow match on undefined tag or when tag is latest
     * in this or that.
     */
    override def equals(that: Any) = that match {
      case ImageName(n, p, t) =>
        name == n && p == prefix && (t == tag || {
          val thisTag = tag.getOrElse(ImageName.defaultImageTag)
          val thatTag = t.getOrElse(ImageName.defaultImageTag)
          thisTag == thatTag
        })

      case _ => false
    }
  }

  protected[core] object ImageName {
    protected val defaultImageTag = "latest"
    private val componentRegex = """([a-z0-9._-]+)""".r
    private val tagRegex = """([\w.-]{0,128})""".r

    /**
     * Constructs an ImageName from a string. This method checks that the image name conforms
     * to the Docker naming. As a result, failure to deserialize a string will throw an exception
     * which fails the Try. Callers could use this to short-circuit operations (CRUD or activation).
     * Internal container names use the proper constructor directly.
     */
    def fromString(s: String): Try[ImageName] =
      Try {
        val parts = s.split("/")

        val (name, tag) = parts.last.split(":") match {
          case Array(componentRegex(s))              => (s, None)
          case Array(componentRegex(s), tagRegex(t)) => (s, Some(t))
          case _                                     => throw DeserializationException("image name is not valid")
        }

        val prefixParts = parts.dropRight(1)
        if (!prefixParts.forall(componentRegex.pattern.matcher(_).matches)) {
          throw DeserializationException("image prefix not is not valid")
        }
        val prefix = if (prefixParts.nonEmpty) Some(prefixParts.mkString("/")) else None

        ImageName(name, prefix, tag)
      } recoverWith {
        case t: DeserializationException => Failure(t)
        case t                           => Failure(DeserializationException("could not parse image name"))
      }
  }

  /**
   * A runtime family manifest is a collection of runtimes grouped by a family (e.g., swift with versions swift:2 and swift:3).
   * @param family runtime family
   * @version set of runtime manifests
   */
  protected[entity] case class RuntimeFamily(name: String, versions: Set[RuntimeManifest])

  /**
   * A collection of runtime families.
   *
   * @param set of supported runtime families
   */
  protected[core] case class Runtimes(runtimes: Set[RuntimeFamily],
                                      blackboxImages: Set[ImageName],
                                      prewarmingConfigs: Set[PrewarmingConfig],
                                      bypassPullForLocalImages: Option[String]) {

    val knownContainerRuntimes: Set[String] = runtimes.flatMap(_.versions.map(_.kind))

    def skipDockerPull(image: ImageName): Boolean = {
      blackboxImages.contains(image) ||
      image.prefix.flatMap(p => bypassPullForLocalImages.map(_ == p)).getOrElse(false)
    }

    def toJson: JsObject = {
      runtimes
        .map { family =>
          family.name -> family.versions.map(_.toJsonSummary)
        }
        .toMap
        .toJson
        .asJsObject
    }

    def resolveDefaultRuntime(kind: String): Option[RuntimeManifest] = {
      kind match {
        case defaultSplitter(family) => defaultRuntimes.get(family).flatMap(manifests.get(_))
        case _                       => manifests.get(kind)
      }
    }

    val manifests: Map[String, RuntimeManifest] = {
      runtimes.flatMap {
        _.versions.map { m =>
          m.kind -> m
        }
      }.toMap
    }

    private val defaultRuntimes: Map[String, String] = {
      runtimes.map { family =>
        family.versions.filter(_.default.exists(identity)).toList match {
          case Nil if family.versions.size == 1 => family.name -> family.versions.head.kind
          case Nil                              => throw new IllegalArgumentException(s"${family.name} has multiple versions, but no default.")
          case d :: Nil                         => family.name -> d.kind
          case ds =>
            throw new IllegalArgumentException(s"Found more than one default for ${family.name}: ${ds.mkString(",")}.")
        }
      }.toMap
    }

    private val defaultSplitter = "([a-z0-9]+):default".r
  }

  protected[entity] implicit val imageNameSerdes = jsonFormat3(ImageName.apply)
  protected[entity] implicit val runtimeManifestSerdes = jsonFormat8(RuntimeManifest)
}
