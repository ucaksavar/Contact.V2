package com.example.contactv2.viewmodel

import android.app.Application
import android.content.ContentProviderOperation
import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contactv2.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val turkishCollator = Collator.getInstance(Locale("tr", "TR"))
    private var sharedPrefs: SharedPreferences? = null

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _password = MutableStateFlow<String?>(null)
    val password: StateFlow<String?> = _password.asStateFlow()

    private val _hiddenContactIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenContactIds: StateFlow<Set<String>> = _hiddenContactIds.asStateFlow()

    private val _isFakeCallDark = MutableStateFlow(false)
    val isFakeCallDark: StateFlow<Boolean> = _isFakeCallDark.asStateFlow()

    val filteredContacts: StateFlow<List<Contact>> = combine(_contacts, _searchQuery, _hiddenContactIds) { contacts, query, hiddenIds ->
        val base = contacts.filter { it.id !in hiddenIds }
        if (query.isEmpty()) base else {
            val q = query.lowercase(Locale("tr", "TR"))
            base.filter { it.name.lowercase(Locale("tr", "TR")).contains(q) || it.phoneNumber.contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenContacts: StateFlow<List<Contact>> = combine(_contacts, _searchQuery, _hiddenContactIds) { contacts, query, hiddenIds ->
        val base = contacts.filter { it.id in hiddenIds }
        if (query.isEmpty()) base else {
            val q = query.lowercase(Locale("tr", "TR"))
            base.filter { it.name.lowercase(Locale("tr", "TR")).contains(q) || it.phoneNumber.contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initPrefs(getApplication())
        fetchContacts(getApplication())
        startExpiryCheck()
    }

    private fun startExpiryCheck() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val prefs = sharedPrefs ?: break
                val expiryMap = prefs.all.filter { it.key.startsWith("expiry_") }
                
                var changed = false
                expiryMap.forEach { (key, value) ->
                    val expiryTime = value as? Long ?: 0L
                    if (expiryTime > 0 && now >= expiryTime) {
                        val contactId = key.removePrefix("expiry_")
                        deleteSystemContact(contactId)
                        prefs.edit().remove(key).apply()
                        changed = true
                    }
                }
                
                if (changed) {
                    fetchContacts(getApplication())
                }
                delay(12 * 60 * 60 * 1000) // 12 Saatte bir kontrol
            }
        }
    }

    fun initPrefs(context: Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences("ContactV2Prefs", Context.MODE_PRIVATE)
            _password.value = sharedPrefs?.getString("hidden_password", null)
            _hiddenContactIds.value = sharedPrefs?.getStringSet("hidden_contact_ids", emptySet()) ?: emptySet()
            _isFakeCallDark.value = sharedPrefs?.getBoolean("fake_call_dark", false) ?: false
        }
    }

    fun toggleFakeCallTheme() {
        val newVal = !_isFakeCallDark.value
        _isFakeCallDark.value = newVal
        sharedPrefs?.edit()?.putBoolean("fake_call_dark", newVal)?.apply()
    }

    fun setPassword(newPassword: String) {
        _password.value = newPassword
        sharedPrefs?.edit()?.putString("hidden_password", newPassword)?.apply()
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun fetchContacts(context: Context) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val newList = mutableListOf<Contact>()
                val existingNumbers = HashSet<String>()
                
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )
                
                cursor?.use {
                    val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    if (idIdx != -1 && nameIdx != -1 && numIdx != -1) {
                        while (it.moveToNext()) {
                            val id = it.getString(idIdx)
                            val name = it.getString(nameIdx) ?: "İsimsiz"
                            val num = it.getString(numIdx) ?: ""
                            if (existingNumbers.add(num)) {
                                val expiry = sharedPrefs?.getLong("expiry_$id", 0L).takeIf { t -> t != 0L }
                                newList.add(Contact(id, name, num, expiryTimestamp = expiry))
                            }
                        }
                    }
                }
                newList.sortedWith { c1, c2 -> turkishCollator.compare(c1.name, c2.name) }
            }
            _contacts.value = list
        }
    }

    fun addContact(context: Context, name: String, number: String, isTemporary: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val ops = arrayListOf<ContentProviderOperation>()
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
            
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())
            
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
            
            try {
                val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                val rawContactUri = results[0].uri
                if (rawContactUri != null && isTemporary) {
                    val rawContactId = rawContactUri.lastPathSegment
                    if (rawContactId != null) {
                        val expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                        sharedPrefs?.edit()?.putLong("expiry_$rawContactId", expiryTime)?.apply()
                    }
                }
                withContext(Dispatchers.Main) { fetchContacts(context) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateContact(context: Context, contact: Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            val ops = arrayListOf<ContentProviderOperation>()
            
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", 
                    arrayOf(contact.id, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name).build())
            
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?", 
                    arrayOf(contact.id, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE))
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phoneNumber).build())
            
            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                withContext(Dispatchers.Main) { fetchContacts(context) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun deleteSystemContact(id: String) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts._ID} = ? OR ${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(id, id))
            .build())
        try {
            getApplication<Application>().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun toggleContactVisibility(id: String) {
        val current = _hiddenContactIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _hiddenContactIds.value = current
        sharedPrefs?.edit()?.putStringSet("hidden_contact_ids", current)?.apply()
    }
}
