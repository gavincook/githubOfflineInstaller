# GitHub Windows离线安装包制作

### 代码制作安装包
直接启动`Fetcher`,然后在命令行中输入需要制作安装包的路径即可。 

程序中默认使用的是`3.0.9.0`版本。如需要下载其他版本，直接修改该版本号即可，注意使用`_`代替`.`即可。

如果不知道版本号，则可以先随意指定一个版本号，然后运行`Fetcher`,会在输出目录下载`GitHub.application`,然后直接运行该文件，如果遇到网络问题则会出现错误日志，查看该错误日志就可以看到GitHub最新版本号。

同样也可以从GitHub官网上查看; https://desktop.github.com/release-notes/windows/ ，只需要在对应版本号后面加`_0`即可

### 真麻烦，我要直接下载
如果需要直接下载`v3.0.9.0`版本
* 可以直接到release中下载: https://github.com/gavincook/githubOfflineInstaller/releases/tag/3.0.9.0
* 如果下载速度慢，可以使用百度云盘的下载地址：http://pan.baidu.com/s/1eRqx0nK
