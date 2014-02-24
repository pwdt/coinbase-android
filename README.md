coinbase-android
================

The official Android application for [Coinbase](https://coinbase.com/).

## Features
* Send/request bitcoin payments using email, QR codes, or NFC
* Buy and sell bitcoin right from your mobile phone
* View transaction history, details, and balance
* See prices in BTC or your native currency
* Support for multiple accounts
* 100% open source - contributions welcome
* Revoke [access](https://coinbase.com/account/integrations) remotely if you lose your phone.

You can find more information, and download the app, at https://play.google.com/store/apps/details?id=com.coinbase.android.

## Building

Building the app is only supported in [Android Studio](http://developer.android.com/sdk/installing/studio.html). Steps to build:

1.  `git clone git@github.com:coinbase/coinbase-android.git`
2.	Open Android Studio, and close any open project
3.	Click 'Import project...'
4.	Open the `coinbase-android` directory downloaded in step 1
5.  That's it! You should be able to build and run the app from inside Android Studio.

Contributions are welcome - please send a pull request.

## Translations

We welcome crowdsourced translations! Just submit a pull request including the edited files. Create a new folder

> coinbase-android/res/values-xx_XX

where xx is your language code and XX is your country code (a partial list can be found at https://developer.android.com/reference/java/util/Locale.html).
Copy and paste the `coinbase-android/res/values/strings.xml` file into the new folder, and edit the file to add your translations. Thanks!
