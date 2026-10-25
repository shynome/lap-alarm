package looply.shynome.com

import android.app.Application
import looply.shynome.com.core.Looply

class LooplyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Looply.init(this)
    }
}
