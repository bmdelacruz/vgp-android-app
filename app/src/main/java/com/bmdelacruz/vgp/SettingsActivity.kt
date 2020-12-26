package com.bmdelacruz.vgp

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.util.PatternsCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.settings_activity)
        setSupportActionBar(findViewById(R.id.toolbar))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            preferenceScreen
                .findPreference<EditTextPreference>(getString(R.string.pref_key_server_address))!!
                .setOnPreferenceChangeListener { _, newValue ->
                    newValue as String?

                    val isValid = newValue != null &&
                            PatternsCompat.WEB_URL.matcher(newValue).matches()

                    if (!isValid) {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_title_invalid_server_address)
                            .setMessage(R.string.dialog_message_invalid_server_address)
                            .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                            .show()
                    }

                    isValid
                }

            preferenceScreen
                .findPreference<ListPreference>(getString(R.string.pref_key_theme_mode))!!
                .setOnPreferenceChangeListener { _, newValue ->
                    newValue as String?

                    when (newValue) {
                        getString(R.string.theme_mode_light) -> {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        }
                        getString(R.string.theme_mode_dark) -> {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        }
                        getString(R.string.theme_mode_system) -> {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        }
                        else -> {
                        }
                    }

                    true
                }
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            val retVal = super.onPreferenceTreeClick(preference)
            return when (preference?.key) {
                getString(R.string.pref_key_help) -> {
                    // TODO
                    true
                }
                getString(R.string.pref_key_about) -> {
                    // TODO
                    true
                }
                else -> retVal
            }
        }
    }
}