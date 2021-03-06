/*
 * Copyright (C) 2017 Wiktor Nizio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.org.seva.navigator

import android.app.Application
import android.content.Intent

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

import javax.inject.Inject

import pl.org.seva.navigator.model.database.sqlite.DbHelper
import pl.org.seva.navigator.model.database.sqlite.SqlReader
import pl.org.seva.navigator.model.database.sqlite.SqlWriter
import pl.org.seva.navigator.presenter.FriendshipListener
import pl.org.seva.navigator.source.ActivityRecognitionSource
import pl.org.seva.navigator.model.ContactsStore
import pl.org.seva.navigator.source.FriendshipSource
import pl.org.seva.navigator.model.Login

class NavigatorApplication : Application() {

    @Inject
    lateinit var activityRecognitionSource: ActivityRecognitionSource
    @Inject
    lateinit var sqlWriter: SqlWriter
    @Inject
    lateinit var sqlReader: SqlReader
    @Inject
    lateinit var contactsStore: ContactsStore
    @Inject
    lateinit var friendshipSource: FriendshipSource
    @Inject
    lateinit var friendshipListener: FriendshipListener
    @Inject
    lateinit var login: Login

    lateinit var component: NavigatorComponent
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        component = createComponent()
        component.inject(this)
        login.setCurrentUser(FirebaseAuth.getInstance().currentUser)
        activityRecognitionSource.initGoogleApiClient(this)
        val helper = DbHelper(this)
        sqlWriter.setHelper(helper)
        sqlReader.setHelper(helper)
        contactsStore.addAll(sqlReader.friends)
        friendshipListener.init(this)
        if (login.isLoggedIn) {
            setFriendshipListeners()
            startService()
        }
    }

    private fun createComponent(): NavigatorComponent {
        return DaggerNavigatorComponent.create()
    }

    fun login(user: FirebaseUser) {
        login.setCurrentUser(user)
        setFriendshipListeners()
        restoreFriendsFromServer()
        startService()
    }

    fun logout() {
        stopService()
        clearFriendshipListeners()
        contactsStore.clear()
        login.setCurrentUser(null)
    }

    private fun setFriendshipListeners() {
        friendshipSource.addFriendshipListener(friendshipListener)
    }

    private fun restoreFriendsFromServer() {
        friendshipSource.downloadFriendsFromServer { contactsStore.add(it) }
    }

    private fun clearFriendshipListeners() {
        friendshipSource.clearFriendshipListeners()
    }

    fun startService() {
        if (isServiceRunning) {
            return
        }
        startService(Intent(baseContext, NavigatorService::class.java))
        isServiceRunning = true
    }

    fun stopService() {
        if (!isServiceRunning) {
            return
        }
        stopService(Intent(baseContext, NavigatorService::class.java))
        isServiceRunning = false
    }
}
