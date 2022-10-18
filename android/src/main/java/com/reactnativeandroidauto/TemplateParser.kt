package com.reactnativeandroidauto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.car.app.navigation.model.*
import androidx.core.graphics.drawable.IconCompat
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.image.CloseableBitmap
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.views.imagehelper.ImageSource
import java.util.*

class TemplateParser internal constructor(
  private val context: CarContext,
  private val reactCarRenderContext: ReactCarRenderContext
) {
  fun parseTemplate(renderMap: ReadableMap): Template {
    return when (renderMap.getString("type")) {
      "list-template" -> parseListTemplateChildren(renderMap)
      "place-list-map-template" -> parsePlaceListMapTemplate(renderMap)
      "pane-template" -> parsePaneTemplate(renderMap)
      "navigation-template" -> parseNavigationTemplate(renderMap)
      else -> PaneTemplate.Builder(
        Pane.Builder().setLoading(true).build()
      ).setTitle("Pane Template").build()
    }
  }

  private fun parseActionStrip(map: ReadableMap?): ActionStrip? {
    val builder = ActionStrip.Builder()
    return if (map != null) {
      val actions = map.getArray("actions")
      for (i in 0 until actions!!.size()) {
        val actionMap = actions.getMap(i)
        val action = parseAction(actionMap)
        builder.addAction(action)
      }
      builder.build()
    } else {
      null
    }
  }

  private fun parseCarIcon(map: ReadableMap): CarIcon {
    val source = ImageSource(context, map.getString("uri"))
    val imageRequest = ImageRequestBuilder.newBuilderWithSource(source.uri).build()
    val dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, context)
    val result = DataSources.waitForFinalResult(dataSource) as CloseableReference<CloseableBitmap>
    val bitmap = result.get().underlyingBitmap

    CloseableReference.closeSafely(result)
    dataSource.close()

    return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
  }

  private fun parseAction(map: ReadableMap?): Action {
    val builder = Action.Builder()
    if (map != null) {
      map.getString("title")?.let {
        builder.setTitle(it)
      }
      map.getMap("icon")?.let {
        builder.setIcon(parseCarIcon(it))
      }
      try {
        builder.setBackgroundColor(getColor(map.getString("backgroundColor")))
      } catch (e: Exception) {
        e.printStackTrace()
      }
      try {
        val onPress = map.getInt("onPress")
        builder.setOnClickListener { invokeCallback(onPress) }
      } catch (e: Exception) {
        Log.d("AUTO", "Couldn't parseAction", e)
        e.printStackTrace()
      }
    }
    return builder.build()
  }

  private fun parseNavigationInfo(map: ReadableMap): NavigationTemplate.NavigationInfo {
    val type = map.getString("type")
    return if (type == "routingInfo") {
      parseRoutingInfo(map.getMap("info")!!)
    } else {
      parseMessageInfo(map.getMap("info")!!)
    }
  }

  private fun parseRoutingInfo(map: ReadableMap): RoutingInfo {
    return RoutingInfo.Builder()
      .apply {
        setCurrentStep(parseStep(map.getMap("step")!!), parseDistance(map.getMap("distance")!!))
        setJunctionImage(parseCarIcon(map.getMap("junctionImage")!!))
        setLoading(map.getBoolean("isLoading"))
        setNextStep(parseStep(map.getMap("nextStep")!!))
      }.build()
  }

  private fun parseDistance(map: ReadableMap): Distance {
    return Distance.create(map.getDouble("displayDistance"), map.getInt("displayUnit"))
  }

  private fun parseStep(map: ReadableMap): Step {
    return Step.Builder().apply {
      val lane = parseLane(map.getMap("lane")!!)
      addLane(lane)
      setCue(map.getString("cue")!!)
      setLanesImage(parseCarIcon(map.getMap("lanesImage")!!))
      setManeuver(parseManeuver(map.getMap("maneuver")!!))
      setRoad(map.getString("road")!!)
    }.build()
  }

  private fun parseLane(map: ReadableMap): Lane {
    val laneBuilder = Lane.Builder()
    val shape = map.getInt("shape")
    val isRecommended = map.getBoolean("isRecommended")
    return laneBuilder.addDirection(LaneDirection.create(shape, isRecommended)).build()
  }

  private fun parseManeuver(map: ReadableMap): Maneuver {
    val type = map.getInt("type")
    val builder = Maneuver.Builder(type)
    builder.setIcon(parseCarIcon(map.getMap("icon")!!))
    if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE
      || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE
    ) {
      builder.setRoundaboutExitAngle(map.getInt("roundaboutExitAngle"))
    }

    if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
      || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
      || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE
      || type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE
    ) {
      builder.setRoundaboutExitNumber(map.getInt("roundaboutExitNumber"))
    }

    return builder.build()
  }

  private fun parseMessageInfo(map: ReadableMap): MessageInfo {
    val builder = MessageInfo.Builder(map.getString("title")!!)
    builder.setImage(parseCarIcon(map.getMap("icon")!!))
    return builder.build()
  }

  private fun parseTravelEstimate(map: ReadableMap): TravelEstimate {
    val dateTimeMap = map.getMap("destinationTime")!!
    val destinationDateTime = DateTimeWithZone.create(
      dateTimeMap.getDouble("timeSinceEpochMillis").toLong(),
      TimeZone.getTimeZone(dateTimeMap.getString("id")),
    )
    val builder = TravelEstimate.Builder(
      parseDistance(map.getMap("remainingDistance")!!),
      destinationDateTime,
    )
    builder.setRemainingTimeSeconds(map.getDouble("remainingTimeSeconds").toLong())
    return builder.build()
  }


  private fun getColor(colorName: String?): CarColor {
    return when (colorName) {
      "blue" -> CarColor.BLUE
      "green" -> CarColor.GREEN
      "primary" -> CarColor.PRIMARY
      "red" -> CarColor.RED
      "secondary" -> CarColor.SECONDARY
      "yellow" -> CarColor.YELLOW
      "default" -> CarColor.DEFAULT
      else -> CarColor.DEFAULT
    }
  }

  private fun parseNavigationTemplate(map: ReadableMap): NavigationTemplate {
    val builder = NavigationTemplate.Builder()
    try {
      val actionStrip = parseActionStrip(map.getMap("actionStrip"))!!
      builder.setActionStrip(actionStrip)
      val mapActionStrip = map.getMap("mapActionStrip")
      mapActionStrip?.let {
        builder.setMapActionStrip(parseActionStrip(it)!!)
      }
      map.getMap("navigationInfo")?.let {
        builder.setNavigationInfo(parseNavigationInfo(it))
      }
      map.getMap("destinationTravelEstimate")?.let {
        builder.setDestinationTravelEstimate(parseTravelEstimate(it))
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return builder.build()
  }

  private fun parsePaneTemplate(map: ReadableMap): PaneTemplate {
    val paneBuilder = Pane.Builder()
    val children = map.getArray("children")
    val loading: Boolean = try {
      map.getBoolean("isLoading")
    } catch (e: Exception) {
      children == null || children.size() == 0
    }

    paneBuilder.setLoading(loading)
    val actions = mutableListOf<Action>()
    if (!loading) {
      for (i in 0 until children!!.size()) {
        val child = children.getMap(i)
        val type = child.getString("type")
        Log.d("AUTO", "Adding child to row")
        if (type == "row") {
          paneBuilder.addRow(buildRow(child))
        }
        if (type == "action") {
          actions.add(parseAction(child))
        }
      }
      for (i in actions.indices) {
        paneBuilder.addAction(actions[i])
      }
    }
    val builder = PaneTemplate.Builder(paneBuilder.build())
    val title = map.getString("title")
    if (title == null || title.isEmpty()) {
      builder.setTitle("<No Title>")
    } else {
      builder.setTitle(title)
    }
    try {
      builder.setHeaderAction(getHeaderAction(map.getString("headerAction"))!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    try {
      val actionStripMap = map.getMap("actionStrip")
      builder.setActionStrip(parseActionStrip(actionStripMap)!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return builder.build()
  }

  private fun parsePlaceListMapTemplate(map: ReadableMap): PlaceListMapTemplate {
    val builder = PlaceListMapTemplate.Builder()
    builder.setTitle(map.getString("title")!!)
    val children = map.getArray("children")
    try {
      builder.setHeaderAction(getHeaderAction(map.getString("headerAction"))!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    val loading: Boolean = try {
      map.getBoolean("isLoading")
    } catch (e: Exception) {
      children == null || children.size() == 0
    }
    Log.d("AUTO", "Rendering " + if (loading) "Yes" else "No")
    builder.setLoading(loading)
    if (!loading) {
      val itemListBuilder = ItemList.Builder()
      for (i in 0 until children!!.size()) {
        val child = children.getMap(i)
        val type = child.getString("type")
        Log.d("AUTO", "Adding $type to row")
        if (type == "row") {
          itemListBuilder.addItem(buildRow(child))
        }
      }
      builder.setItemList(itemListBuilder.build())
    }
    try {
      val actionStripMap = map.getMap("actionStrip")
      builder.setActionStrip(parseActionStrip(actionStripMap)!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return builder.build()
  }

  private fun parseListTemplateChildren(map: ReadableMap): ListTemplate {
    val children = map.getArray("children")
    val builder = ListTemplate.Builder()
    val loading: Boolean = try {
      map.getBoolean("isLoading")
    } catch (e: Exception) {
      children!!.size() == 0
    }
    builder.setLoading(loading)
    if (!loading) {
      for (i in 0 until children!!.size()) {
        val child = children.getMap(i)
        val type = child.getString("type")
        if (type == "item-list") {
          builder.addSectionedList(
            SectionedItemList.create(
              parseItemListChildren(child),
              child.getString("header")!!
            )
          )
        }
      }
    }
    try {
      builder.setHeaderAction(getHeaderAction(map.getString("headerAction"))!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    try {
      val actionStripMap = map.getMap("actionStrip")
      builder.setActionStrip(parseActionStrip(actionStripMap)!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    builder.setTitle(map.getString("title")!!)
    return builder.build()
  }

  private fun parseItemListChildren(itemList: ReadableMap): ItemList {
    val children = itemList.getArray("children")
    val builder = ItemList.Builder()
    for (i in 0 until children!!.size()) {
      val child = children.getMap(i)
      val type = child.getString("type")
      if (type == "row") {
        builder.addItem(buildRow(child))
      }
    }
    try {
      builder.setNoItemsMessage(itemList.getString("noItemsMessage")!!)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return builder.build()
  }

  private fun buildRow(rowRenderMap: ReadableMap): Row {
    val builder = Row.Builder()
    builder.setTitle(rowRenderMap.getString("title")!!)
    try {
      val texts = rowRenderMap.getArray("texts")
      for (i in 0 until texts!!.size()) {
        builder.addText(texts.getString(i))
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    try {
      val onPress = rowRenderMap.getInt("onPress")
      builder.setBrowsable(true)
      builder.setOnClickListener { invokeCallback(onPress) }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    try {
//            builder.setMetadata(parseMetaData(rowRenderMap.getMap("metadata")));
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return builder.build()
  }

  private fun getHeaderAction(actionName: String?): Action? {
    return if (actionName == null) {
      null
    } else {
      when (actionName) {
        "back" -> Action.BACK
        "app_icon" -> Action.APP_ICON
        else -> null
      }
    }
  }

  private fun invokeCallback(callbackId: Int, parameters: WritableNativeMap? = null) {
    var params = parameters
    if (params == null) {
      params = WritableNativeMap()
    }
    params.putInt("id", callbackId)
    params.putString("screen", reactCarRenderContext.screenMarker)
    reactCarRenderContext.eventCallback!!.invoke(params)
  }

  companion object {
    const val TAG = "TemplateParser"
  }
}
