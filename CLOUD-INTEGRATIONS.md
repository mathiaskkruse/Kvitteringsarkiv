# Cloud-integrationer

## Arkitektur

`StorageProvider` er den fælles kontrakt. Scanner, OCR, parser og UI kender derfor ikke forskel på Google Drive, OneDrive, Dropbox eller en lokal/mappebaseret destination.

## Google Drive

Brug OAuth 2.0 og Google Drive API v3. For første produktionsversion bør appen bruge den mindst privilegerede tilladelse, der opfylder mappe/upload-flowet, og undgå fuld Drive-adgang medmindre den reelt behøves.

Kræver før aktivering:
- Google Cloud-projekt.
- Drive API aktiveret.
- OAuth consent screen.
- Android OAuth-klient knyttet til package name + SHA fingerprint.

## OneDrive

Brug Microsoft Authentication Library (MSAL) til Android og Microsoft Graph til filer/mapper.

Kræver før aktivering:
- App-registrering i Microsoft Entra.
- Android redirect URI/package/signature hash.
- Delegerede filrettigheder til Graph.

## Dropbox

Brug Dropbox OAuth 2.0 og Files API.

Kræver før aktivering:
- Dropbox App Console-app.
- Redirect URI / app key.
- Begrænsede fil-scopes.

## Sikkerhed

Klient-hemmeligheder må ikke embeddes i APK'en. Mobile apps behandles som public clients. Tokens skal opbevares via udbyderens anbefalede SDK/token-cache eller Android Keystore-beskyttet storage.
