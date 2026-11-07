// Android persists these fully qualified names in widgets and PendingIntents across updates.
// Keep only forwarding entry points here; all app behavior belongs to the XRec package.
package com.example.timerecorder

class ActiveTaskService : io.github.hurokume.xrec.ActiveTaskService()

class TaskActionReceiver : io.github.hurokume.xrec.TaskActionReceiver()

class TemporaryTaskWidgetProvider : io.github.hurokume.xrec.TemporaryTaskWidgetProvider()
