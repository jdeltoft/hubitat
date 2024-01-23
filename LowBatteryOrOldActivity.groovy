//import org.apache.commons.lang3.time.DateUtils
import java.text.SimpleDateFormat 

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
// - need alert to share device details
// - add setting for night hours
// - auto trigger a refresh of device if stale comms, before reporting alert (smoke detector worked for example)
// - 

def mainPage() {
  dynamicPage(name: "mainPage", title: "Low Battery or Old Activity Alert (ver:0.13)", install: true, uninstall: true) {
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
      input "minHoursBetweenAlerts", "number", title: "Minimum Hours Between Alerts:", defaultValue: 24, submitOnChange: true
      input "avoidNightAlerts", "bool", title: "Prevent Alerts at Night", defaultValue: true, submitOnChange: true
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

def getHour() {
	def cal = Calendar.getInstance()
	return cal[Calendar.HOUR_OF_DAY]
}

def lastAlarmTooRecent() {
  if (state.lastAlarmTimestamp == 0) {
    return false
  } else if ((now() - state.lastAlarmTimestamp) < (minHoursBetweenAlerts * 60 * 60 * 1000)) {
    //if (debugLog) log.debug "LowBatt: last alarm too recent (now:${now()}  last:{$state.lastAlarmTimestamp})"
    return true
  } else {
    return false
  }
}

def initialize() {
  state.lastAlarmTimestamp = 0

  subscribe(contactSensors, "battery", handlerBatteryEvent)
  subscribe(motionSensors, "battery", handlerBatteryEvent)
  subscribe(waterSensors, "battery", handlerBatteryEvent)
  subscribe(smokSensors, "battery", handlerBatteryEvent)

  log.debug "LowBatt: installation initialized..."
}

def isBatteryLowOrCommunicationStale(evt, sensors) {
	def foundLowOrStale = ""
	sensors.each {
    if (it.currentBattery <= lowBatteryThreshold) {
      if (it.device.id == 757) {
        // TODO: bad device that won't go above 66 % battery ???
        if (debugLog) log.debug "LowBatt: IGNORING!! battery for $it.device.label ($it.currentBattery%)"
      } else {
        if (debugLog) log.debug "LowBatt: found low battery for $it.device.label ($it.currentBattery%)"
	      foundLowOrStale = "Batt:" + foundLowOrStale + it.device.label + ", "
      }
    }

    // times are is in msec
    def deviceStaleDays = (Math.floor((now() - it.device.lastActivityTime.getTime()) / (864 * Math.pow(10,5)))).toInteger()

    if (deviceStaleDays >= oldActivityThreshold) {
      if (debugLog) log.debug "LowBatt: found stale comms for $it.device.label ($deviceStaleDays days)"
	    foundLowOrStale = "Comm:" + foundLowOrStale + it.device.label + ", "
    }
	}
  return foundLowOrStale
}

def handlerBatteryEvent(evt) {
  if (lastAlarmTooRecent() == true) {
    return
  }

  def totalErrorMessage = ""

  totalErrorMessage = totalErrorMessage + isBatteryLowOrCommunicationStale(evt, contactSensors)
  totalErrorMessage = totalErrorMessage + isBatteryLowOrCommunicationStale(evt, motionSensors)
  totalErrorMessage = totalErrorMessage + isBatteryLowOrCommunicationStale(evt, waterSensors)
  totalErrorMessage = totalErrorMessage + isBatteryLowOrCommunicationStale(evt, smokeSensors)

  if (totalErrorMessage != "") {
    triggerAlarm(totalErrorMessage)
  }
}

def triggerAlarm(errorMessage) {
  if (avoidNightAlerts == true) {
    // TODO: make these hours settable in UI
    if (getHour() > 20 || getHour() < 7) {
      //if (debugLog) log.debug "LowBatt: waiting till daytime for alarm..."
      return
    }
  }

  if (debugLog) log.debug "LowBatt: alarm triggered ..."

  state.lastAlarmTimestamp = now()
  send(alertMessage + " :: " + errorMessage)
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

