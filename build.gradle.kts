
import com.akuleshov7.dt.buildutils.configureVersioning
import com.akuleshov7.dt.buildutils.createDetektTask
import com.akuleshov7.dt.buildutils.createDiktatTask
import com.akuleshov7.dt.buildutils.installGitHooks

plugins {
    id("com.cdsap.talaiot.plugin.base") version "1.4.2"
}

configureVersioning()

createDiktatTask()
createDetektTask()
installGitHooks()

talaiot {
    publishers {
        timelinePublisher = true
    }
}
