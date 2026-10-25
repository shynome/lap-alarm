package lap.shynome.com

import android.app.Application
import lap.shynome.com.core.Lap

class LapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lap.init(this)
    }
}
