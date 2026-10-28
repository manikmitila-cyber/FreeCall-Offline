# FreeCall Offline — Included in this build

## Account/Profile
- Phone number field
- Username field
- Display name
- Local account ID
- Profile photo selection from device gallery
- Profile photo preview
- Profile photo shown on the local profile
- Local persistence using SharedPreferences

## Communication UI
- Nearby/Mesh users list scaffold
- User profile dialog
- Call button UI
- Message button UI
- Mesh status label

## Network architecture scaffold
- Mesh packet models
- Duplicate packet protection / TTL routing scaffold
- Profile relay packet model
- Voice-call session/controller scaffold
- Bluetooth/Wi-Fi permission declarations

## Important limitation
This ZIP contains the app UI and local/account/profile implementation plus the networking architecture scaffold. Real two-phone Wi-Fi Direct/Bluetooth mesh discovery, multi-hop routing, encrypted transport, and live low-latency voice streaming still require device-side networking implementation and testing; they are not honestly represented as fully working merely by the UI scaffold.
