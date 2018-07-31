package tech.ula.ui

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import androidx.navigation.findNavController
import kotlinx.android.synthetic.main.activity_main.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.ula.MainActivity
import tech.ula.R

@RunWith(AndroidJUnit4::class)
class SessionListFragmentTest {

    @get:Rule
    val activityTestRule = ActivityTestRule(MainActivity::class.java)

    @Before
    fun setup() {
        activityTestRule.activity.findNavController(R.id.nav_host_fragment).navigate(R.id.session_list_fragment)
    }

    @Test
    fun addButtonNavigatesToSessionEditPage() {
        onView(withId(R.id.menu_item_add))
                .perform(click())

        onView(withHint(R.string.hint_session_name))
                .check(matches(isDisplayed()))
    }

    @Test
    fun filesystemSelectionNavigatesToFilesystemPage() {
        onView(withId(R.id.filesystem_list_fragment))
                .perform(click())

        onView(withId(R.id.list_filesystems))
                .check(matches(isDisplayed()))
    }
}