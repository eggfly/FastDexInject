# FastDexInject

插入到Application的派生类的attachBaseContext最前面:
```java
@Override
protected void attachBaseContext(Context base) {
    // TODO: 仅用作快速编译，merge代码时不要进去
    DebugFastHotFix.setupClassLoader(base);
    // TODO: END
    super.attachBaseContext(base);
}
```

