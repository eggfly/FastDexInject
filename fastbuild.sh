cd $WEIBO_PROJECT && \
gradle :allmodules:story:compileDebugSources && \
cd allmodules/story/build/intermediates/classes/debug && \
echo "packaging fast.jar..." && \
jar cf fast.jar com/sina/weibo/feed com/sina/weibo/story/ && \
echo "package fast.jar done!" && \
echo "dexing fast.dex..." && \
dx --dex --output fast.dex fast.jar && \
echo "dex fast.dex done!" && \
adb shell mkdir -p /sdcard/Android/data/com.sina.weibo/dex/ && \
adb shell rm -rf /sdcard/Android/data/com.sina.weibo/dex/* && \
adb push fast.dex /sdcard/Android/data/com.sina.weibo/dex/ && \
adb shell am force-stop com.sina.weibo && \
adb shell monkey -p com.sina.weibo -c android.intent.category.LAUNCHER 1 && \
say "done"

tput bel
cd -
