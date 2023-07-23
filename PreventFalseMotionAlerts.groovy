definition(
  name: "Prevent False Motion Alerts",
  namespace: "hubitat",
  author: "Justin Eltoft",
  description: "Require multiple motion sensor events in specified time before alerting of intrusion",
  category: "Security",
  iconUrl: "",
  iconX2Url: "")

preferences {
  page(name: "mainPage")
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Prevent False Motion Alerts (ver:0.10)", install: true, uninstall: true) {
    section {
      input "thisName", "text", title: "Name this Alert", submitOnChange: true, defaultValue: "Prevent False Motion Alerts (NameMe)"
      if(thisName) app.updateLabel("$thisName")

      input "motionSensors", "capability.motionSensor", title: "Select Motion Sensors", submitOnChange: true, required: true, multiple: true
      input "alarmSensor", "capability.contactSensor", title: "Select Virtual Contact Sensor as Intrustion Alarm Flag", submitOnChange: true, required: true, multiple: false
      input "activityTimeoutMinutes", "number", title: "Activity Timeout (Minutes):", defaultValue: 5, submitOnChange: true, required: true
      input "activationWindowMinutes", "number", title: "Activation Window (Minutes):", defaultValue: 15, submitOnChange: true, required: true
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
  deleteChildDevice("PreventFalseMotionAlerts_${app.id}")
}

def initialize() {
  state.activityTimeout = 0
  state.activationWindowTimeout = 0

  def multipleMotionDev = getChildDevice("PreventFalseMotionAlerts_${app.id}")
  if(!multipeMotionDev) multipeMotionDev = addChildDevice("hubitat", "Virtual Contact Sensor", "PreventFalseMotionAlerts_${app.id}", null, [label: thisName, name: thisName])

  handleMotionEvent()  // need to check for motion at start up also

  subscribe(motionSensors, "motion", handlerMotion)
}

def handlerMotion(evt) {
  if (debugLog) log.debug "PFMA: motion event $evt.device $evt.value"
  handleMotionEvent(evt.device)
}

def handleMotionEvent(dev) {
  def multipeMotionDev = getChildDevice("PreventFalseMotionAlerts_${app.id}")

  // check if we're already activated for multiple motion
  if(state.activityTimeout != 0) {
    if (debugLog) log.debug "PFMA: motion event when triggered"

    // set flag for any motion in triggered state
    multipeMotionDev.open()

    // reset the timeout
    state.activityTimeout = now() + (activityTimeoutMinutes * 60 * 1000)

    // set timer to check if timeout passed
    runIn(60 * activityTimeoutMinutes, timerHandler)
  } else if ((state.activationWindowTimeout != 0) && (now() <= state.activationWindowTimeout)){ 
    if (debugLog) log.debug "PFMA: additional motion event occured during activation window"

    // set "flag" that multiple motion events seen during activity window
    multipeMotionDev.open()

    // reset activation window now that we're activated
    state.activationWindowTimeout = 0

    // enter activity timeout period
    state.activityTimeout = now() + (activityTimeoutMinutes * 60 * 1000)

    // set timer to check if timeout passed
    runIn(60 * activityTimeoutMinutes, timerHandler)
  } else {
    if (debugLog) log.debug "PFMA: additional motion event occured during activation window"

    state.activationWindowTimeout = now() + (activationWindowMinutes * 60 * 1000)

    // close the flag (should be already) since we weren't already triggered
    multipeMotionDev.close()
  }
}

def timerHandler() {
  if (state.activityTimeout != 0) {
    if (debugLog) log.debug "PFMA: timer handler (activity timeout)"

    if (now() > state.activityTimeout){
      if (debugLog) log.debug "PFMA: activity timed out"
      state.activityTimeout = 0
      state.activationWindowTimeout = 0
      multipeMotionDev.close()
    }
  } else if (state.activationWindowTimeout != 0) {
    if (debugLog) log.debug "PFMA: timer handler (activation window timeout)"

    if (now() > state.activationWindowTimeout){
      if (debugLog) log.debug "PFMA: activation window timed out"
      state.activityTimeout = 0
      state.activationWindowTimeout = 0
      multipeMotionDev.close()
    }
  } else {
      // all zero, set "flag" closed just in case
      if (debugLog) log.debug "PFMA: timer event with no timeouts"
      multipeMotionDev.close()
  }
}
