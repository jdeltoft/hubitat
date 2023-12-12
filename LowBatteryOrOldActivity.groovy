definition(
  name: "Low Battery or Old Activity Alert",
  namespace: "hubitat",
  author: "Justin Eltoft",
  description: "Alert if a battery powered device has a low battery or has not been active for days",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "")

preferences {
  page(name: "mainPage")
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Low Battery or Old Activity Alert (ver:0.11)", install: true, uninstall: true) {
    section {
      input "thisName", "text", title: "Name this Alert", submitOnChange: true, defaultValue: "Battery/Activity Alert (NameMe)"
      if(thisName) app.updateLabel("$thisName")

      input "contactSensors", "capability.contactSensor", title: "Select Contact Sensor", submitOnChange: true, required: false, multiple: true
      input "motionSensors", "capability.motionSensor", title: "Select Motion Sensor", submitOnChange: true, required: false, multiple: true
      input "waterSensors", "capability.waterSensor", title: "Select Water Sensor", submitOnChange: true, required: false, multiple: true
      input "smokeSensors", "capability.smokeDetector", title: "Select Smoke Detector", submitOnChange: true, required: false, multiple: true
      input "alertMessage", "text", title: "Alert Message", defaultValue: "LowBatt Alert!", required: true 
      input "lowBatteryThreshold", "number", title: "Low Battery Percent (0-100):", defaultValue: 60, submitOnChange: true
      input "oldActivityThreshold", "number", title: "Old Activity Days:", defaultValue: 3, submitOnChange: true
      input "repeat", "bool", title: "repeat:", defaultValue: false, submitOnChange: true
      if (repeat) {
          input "minutesToRepeat", "number", title: "Repeat Alerts Time (minutes):", defaultValue: 30, submitOnChange: true
      }
    }

    section("Notifications") {
      input "sendPushMessage", "capability.notification", title: "Send a push notification?", multiple: true, required: false
    }
    section("Debug") {
      input "debugLog", "bool", title: "Enable Debug Logging:", defaultValue: false, submitOnChange: true
    }
  }
}

def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  initialize()
}

def uninstalled() {
  unsubscribe()
  deleteChildDevice("LowBatteryOldActivityAlert_${app.id}")
}

def initialize() {
  state.alarmTriggerTimestamp = 0

  def outletDevice = getChildDevice("LowBatteryOldActivityAlert_${app.id}")
  if(!outletDevice) outletDevice = addChildDevice("hubitat", "Virtual Contact Sensor", "LowBatteryOldActivityAlert_${app.id}", null, [label: thisName, name: thisName])

  //handleOutletEvent()  // need to check at start up also

  subscribe(contactSensors, "battery", handlerContactBattery)
  subscribe(motionSensors, "battery", handlerMotionBattery)
  subscribe(waterSensors, "battery", handlerWaterBattery)
  subscribe(smokSensors, "battery", handlerSmokeBattery)

  log.debug "LowBatt: installation initialize..."
}

def isThereLowBattery(evt, sensors) {

	def foundLow = false

	//contactSensors.each {
	sensors.each {
    if (it.currentBattery <= lowBatteryThreshold) {
      if ($it.id == 757) {
        // TODO: bad device that won't go above 66 % battery ???
        if (debugLog) log.debug "LowBatt: IGNORING!! battery for $it.device at $it.currentValue"
      } else {
        if (debugLog) log.debug "LowBatt: battery for $it.device at $it.currentValue"
	      foundLow = true
      }
    }
	}
  if (debugLog) log.debug "LowBatt: battery foundLow $foundLow"
  return foundLow
}

def handlerContactBattery(evt) {
  //if (debugLog) log.debug "LowBatt: contact battery event $evt.device $evt.value"

  def outletDevice = getChildDevice("LowBatteryOldActivityAlert_${app.id}")
  if(isThereLowBattery(evt, contactSensors) == true) {
    // show that outlet is off in the virtual device
    outletDevice.open()

    // if outlet is off start a timer for minutesToAlertDay unless already running
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // show that outlet is on in the virtual device
    outletDevice.close()

    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def handlerMotionBattery(evt) {
  //if (debugLog) log.debug "LowBatt: motion battery event $evt.device $evt.value"

  def outletDevice = getChildDevice("LowBatteryOldActivityAlert_${app.id}")
  if(isThereLowBattery(evt, motionSensors) == true) {
    // show that outlet is off in the virtual device
    outletDevice.open()

    // if outlet is off start a timer for minutesToAlertDay unless already running
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // show that outlet is on in the virtual device
    outletDevice.close()

    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def handlerWaterBattery(evt) {
  //if (debugLog) log.debug "LowBatt: water battery event $evt.device $evt.value"

  def outletDevice = getChildDevice("LowBatteryOldActivityAlert_${app.id}")
  if(isThereLowBattery(evt, waterSensors) == true) {
    // show that outlet is off in the virtual device
    outletDevice.open()

    // if outlet is off start a timer for minutesToAlertDay unless already running
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // show that outlet is on in the virtual device
    outletDevice.close()

    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def handlerSmokeBattery(evt) {
  //if (debugLog) log.debug "LowBatt: smoke batter event $evt.device $evt.value"

  def outletDevice = getChildDevice("LowBatteryOldActivityAlert_${app.id}")
  if(isThereLowBattery(evt, smokeSensors) == true) {
    // show that outlet is off in the virtual device
    outletDevice.open()

    // if outlet is off start a timer for minutesToAlertDay unless already running
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // show that outlet is on in the virtual device
    outletDevice.close()

    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def timerHandler(fromInput) {
  if (state.alarmTriggerTimestamp == null) state.alarmTriggerTimestamp = 0

  // Check if alarm timer is active
  if (state.alarmTriggerTimestamp > 0 && (state.alarmTriggerTimestamp < now())) {
    triggerAlarm()
  }

  if (state.alarmTriggerTimestamp > 0) {
    if (debugLog) log.debug "LowBatt: timer set timeout alarm:${state.alarmTriggerTimestamp}"
    runIn(60 * 5, timerHandler)
  }
}

def triggerAlarm() {
  if (state.alarmTriggerTimestamp > 0) {
    state.alarmTriggerTimestamp = 0
    send(alertMessage)

    if (repeat) {
      log.info("LowBatt: repeat new timer for $minutesToRepeat")
      state.alarmTriggerTimestamp = now() + minutesToRepeat * 60 * 1000
    }
  }
}

private send(message) {
  alarmLights.each {
    it.on() 
  }

  if (sendPushMessage != null) {
    log.info("LowBatt: ===> Send Notification: $message")
    sendPushMessage.deviceNotification(message)
  } 
}
