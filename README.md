# GitHub Windows离线安装包制作

### 代码制作安装包
直接启动`Fetcher`,然后在命令行中输入需要制作安装包的路径即可。 

`Fetcher`会将最新版本进行下载。如果只想获取所有相关文件的下载路径，然后使用其他方式，如下载工具下载，则可以修改`Fetcher`的工作模式为`SHOW_URL`：
```
private Mode mode = Mode.SHOW_URL;
```
在`SHOW_URL`模式下，只会打印出需要下载文件的链接。注意：文件会分两个目录。除了启动入口：`GitHub.application`文件在github安装根目录下，其他所有文件应该在：github安装目录/Application Files/GitHub_{版本号(逗号换成了下划线)}

### 真麻烦，我要直接下载
如果需要直接下载`v3.0.9.0`版本
* 可以直接到release中下载: https://github.com/gavincook/githubOfflineInstaller/releases/tag/3.0.9.0
* 如果下载速度慢，可以使用百度云盘的下载地址：http://pan.baidu.com/s/1eRqx0nK

如果需要直接下载`v3.1.1.4`版本
* 可以直接到release中下载: https://github.com/gavincook/githubOfflineInstaller/releases/tag/3.1.1.4
* 如果下载速度慢，可以使用百度云盘的下载地址：http://pan.baidu.com/s/1pLeT3YF

如果需要直接下载`v3.3.4.0`版本
* 可以直接到release中下载: https://github.com/gavincook/githubOfflineInstaller/releases/tag/3.3.4.0
