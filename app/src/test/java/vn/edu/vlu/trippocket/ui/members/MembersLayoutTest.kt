package vn.edu.vlu.trippocket.ui.members

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.DialogMemberNameBinding
import vn.edu.vlu.trippocket.databinding.FragmentMembersBinding
import vn.edu.vlu.trippocket.databinding.ItemMemberBinding

/** The members layouts (made ahead of E1) inflate with the app theme. Owner: Minh. */
@RunWith(RobolectricTestRunner::class)
class MembersLayoutTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_TripPocket)
    private val inflater = LayoutInflater.from(context)
    private val parent = FrameLayout(context)

    @Test
    fun screenRowAndDialog_inflate() {
        val screen = FragmentMembersBinding.inflate(inflater, parent, false)
        assertEquals(context.getString(R.string.members_title), screen.tvMembersTitle.text.toString())
        assertEquals("3 of 15 members", context.resources.getQuantityString(R.plurals.members_count, 3, 3, 15))

        val row = ItemMemberBinding.inflate(inflater, parent, false)
        assertEquals(context.getString(R.string.members_in_use), row.tvInUse.text.toString())
        assertTrue(row.btnRemove.isEnabled)

        val dialog = DialogMemberNameBinding.inflate(inflater, parent, false)
        assertEquals(80, dialog.tilName.counterMaxLength)
    }
}
