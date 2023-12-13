import org.apache.commons.lang3.time.DateUtils

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

// TODO:
// - add device to alert message
// - add check for stale comms

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

  subscribe(contactSensors, "battery", handlerContactBattery)
  subscribe(motionSensors, "battery", handlerMotionBattery)
  subscribe(waterSensors, "battery", handlerWaterBattery)
  subscribe(smokSensors, "battery", handlerSmokeBattery)

  // LOGGING INFO HELP
	smokeSensors.each { 
    //if (debugLog) log.debug "LowBatt: $it.device.properties"

    // lastActivityTime "Tue Dec 12 07:15:26 CST 2023"
    // it.device.lastActivityTime.getTime()

    //def x = (Math.floor((now() - it.device.lastActivityTime.getTime()) / (864 * Math.pow(10,5)))).toInteger()
    //log.debug "LowBatt: $x $it.device.name"
  }

  log.debug "LowBatt: installation initialize... $n"
}

def isThereLowBattery(evt, sensors) {
	def foundLow = false
	sensors.each {
    if (it.currentBattery <= lowBatteryThreshold) {
      if (it.device.id == 757) {
        // TODO: bad device that won't go above 66 % battery ???
        if (debugLog) log.debug "LowBatt: IGNORING!! battery for $it.device"
      } else {
        if (debugLog) log.debug "LowBatt: battery for $it.device"
	      foundLow = true
      }
    }

    // Also check if the lastActivityTime was older than oldActivityThreshold days
    def staleDays = (Math.floor((now() - it.device.lastActivityTime.getTime()) / (864 * Math.pow(10,5)))).toInteger()
    if (staleDays >= oldActivityThreshold) {
      if (debugLog) log.debug "LowBatt: comms for $it.device ($staleDays days)"
	    foundLow = true
    }
	}
  return foundLow
}

def handlerContactBattery(evt) {
  if(isThereLowBattery(evt, contactSensors) == true) {
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def handlerMotionBattery(evt) {
  if(isThereLowBattery(evt, motionSensors) == true) {
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def handlerWaterBattery(evt) {
  if(isThereLowBattery(evt, waterSensors) == true) {
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def handlerSmokeBattery(evt) {
  if(isThereLowBattery(evt, smokeSensors) == true) {
    if (state.alarmTriggerTimestamp == 0) {
      def astroInfo = getSunriseAndSunset(sunsetOffset: sunsetOffset)
      Date latestdate = new Date();

      if (debugLog) log.debug "LowBatt: sending alarm.."
      state.alarmTriggerTimestamp = now()
      triggerAlarm()
    }
  } else { 
    // if outlet is on, set to zero to indicate no timer
    state.alarmTriggerTimestamp = 0
  }
}

def triggerAlarm() {
  if (state.alarmTriggerTimestamp > 0) {
    state.alarmTriggerTimestamp = 0
    send(alertMessage)
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

