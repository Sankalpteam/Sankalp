# Plugins

Main menu plugin section. Plugin-specific modules will be added here later.


## Zoho Mail

Zoho Mail is registered as a separate plugin directly under this directory:

- `zoho_mail/ZohoMailPluginMenuScreen.kt` — connection screen.
- `zoho_mail/ZohoMailPluginApi.kt` — authenticated API client.
- `../src/main/res/drawable/zoho_mail_logo.xml` — plugin icon.

The plugin uses the Sankalp backend for OAuth. Do not put Zoho client secrets or tokens in Android source code.
