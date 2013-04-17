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

<a href="https://dl.dropbox.com/u/324237/coinbase-android/screen1.png"><img src="https://dl.dropbox.com/u/324237/coinbase-android/screen1.png" width="250" /></a>
<a href="https://dl.dropbox.com/u/324237/coinbase-android/screen2.png"><img src="https://dl.dropbox.com/u/324237/coinbase-android/screen2.png" width="250" /></a>
<a href="https://dl.dropbox.com/u/324237/coinbase-android/screen3.png"><img src="https://dl.dropbox.com/u/324237/coinbase-android/screen3.png" width="250" /></a>
<a href="https://dl.dropbox.com/u/324237/coinbase-android/screen4.png"><img src="https://dl.dropbox.com/u/324237/coinbase-android/screen4.png" width="250" /></a>
<a href="https://dl.dropbox.com/u/324237/coinbase-android/screen5.png"><img src="https://dl.dropbox.com/u/324237/coinbase-android/screen5.png" width="250" /></a>
<a href="https://dl.dropbox.com/u/324237/coinbase-android/screen6.png"><img src="https://dl.dropbox.com/u/324237/coinbase-android/screen6.png" width="250" /></a>

## Building

To build the Android app in [Eclipse](http://developer.android.com/sdk/index.html):

1.  `git clone git@github.com:coinbase/coinbase-android.git`
2.	Open Eclipse and go to File > Import... > Android > Existing Code into Android Workspace
3.	Choose the root directory of the cloned project
4.	Deselect all projects and only add: coinbase-android, library-actionbarsherlock, library-slidingmenu
5.  Go to Window > Android SDK Manager and check the box to install "Google APIs" under Android 4.2.2 (this is needed for SlidingMenu)
5. 	The project should now build!

## Translations

We welcome crowdsourced translations! Just submit a pull request including the edited files. Create a new folder

> coinbase-android/res/values-xx_XX

where xx is your language code and XX is your country code (a partial list can be found at https://developer.android.com/reference/java/util/Locale.html).
Copy and paste the `coinbase-android/res/values/strings.xml` file into the new folder, and edit the file to add your translations. Thanks!
