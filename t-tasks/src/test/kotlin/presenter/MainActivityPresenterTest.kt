package presenter

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.teo.ttasks.UserManager
import com.teo.ttasks.data.local.PrefHelper
import com.teo.ttasks.ui.activities.main.MainActivityPresenter
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.Before

class MainActivityPresenterTest : BasePresenterTest() {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var prefHelper: PrefHelper

    @MockK
    private lateinit var userManager: UserManager

    @MockK
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var mainActivityPresenter: MainActivityPresenter

    @Before
    override fun setup() {
        super.setup()
        every { firebaseAuth.addAuthStateListener(any()) } answers { }
        mainActivityPresenter = MainActivityPresenter(context, prefHelper, userManager, firebaseAuth)
    }
}