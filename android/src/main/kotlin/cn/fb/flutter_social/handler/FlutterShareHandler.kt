package cn.fb.flutter_social.handler

import android.graphics.BitmapFactory
import cn.fb.flutter_social.constant.CallResult
import cn.fb.flutter_social.constant.WeChatPluginMethods
import cn.fb.flutter_social.constant.WechatPluginKeys
import cn.fb.flutter_social.utils.ShareImageUtil
import cn.fb.flutter_social.utils.WeChatThumbnailUtil
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
import com.tencent.mm.opensdk.modelmsg.*
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.*

internal class FlutterShareHandler {

    private var channel: MethodChannel? = null

    private var registrar: PluginRegistry.Registrar? = null


    fun setMethodChannel(channel: MethodChannel) {
        this.channel = channel
    }


    fun setRegistrar(registrar: PluginRegistry.Registrar) {
        this.registrar = registrar
    }


    fun handle(call: MethodCall, result: MethodChannel.Result) {
        if (WXAPiHandler.wxApi == null) {
            result.error(CallResult.RESULT_API_NULL, "please config  wxapi first", null)
            return
        }

        if (!WXAPiHandler.wxApi!!.isWXAppInstalled) {
            result.error(CallResult.RESULT_WE_CHAT_NOT_INSTALLED, CallResult.RESULT_WE_CHAT_NOT_INSTALLED, null)
            return
        }

        when (call.method) {
            WeChatPluginMethods.SHARE_TEXT -> shareText(call, result)
            WeChatPluginMethods.SHARE_MINI_PROGRAM -> shareMiniProgram(call, result)
            WeChatPluginMethods.SHARE_IMAGE -> shareImage(call, result)
            WeChatPluginMethods.SHARE_MUSIC -> shareMusic(call, result)
            WeChatPluginMethods.SHARE_VIDEO -> shareVideo(call, result)
            WeChatPluginMethods.SHARE_WEB_PAGE -> shareWebPage(call, result)
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun shareText(call: MethodCall, result: MethodChannel.Result) {
        val textObj = WXTextObject()
        textObj.text = call.argument(WechatPluginKeys.TEXT)
        val msg = WXMediaMessage()
        msg.mediaObject = textObj
        msg.description = call.argument(WechatPluginKeys.TEXT)
        val req = SendMessageToWX.Req()
        req.message = msg
        msg.description

        msg.messageAction = call.argument<String>(WechatPluginKeys.MESSAGE_ACTION)
        msg.messageExt = call.argument<String>(WechatPluginKeys.MESSAGE_EXT)
        msg.mediaTagName = call.argument<String>(WechatPluginKeys.MEDIA_TAG_NAME)

        setCommonArguments(call, req, msg)
        val done = WXAPiHandler.wxApi?.sendReq(req)
        result.success(
                mapOf(
                        WechatPluginKeys.PLATFORM to WechatPluginKeys.ANDROID,
                        WechatPluginKeys.RESULT to done
                )
        )
    }

    private fun shareMiniProgram(call: MethodCall, result: MethodChannel.Result) {
        val miniProgramObj = WXMiniProgramObject()
        miniProgramObj.webpageUrl = call.argument("webPageUrl") // 兼容低版本的网页链接

        // 可选打开 开发版，体验版和正式版
        val type = call.argument("miniProgramType") ?: "release"
        miniProgramObj.miniprogramType = when (type) {
            "test" -> WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_TEST
            "preview" -> WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_PREVIEW
            else -> WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE
        }

        miniProgramObj.userName = call.argument("userName")    // 小程序原始id
        miniProgramObj.path = call.argument("path")            //小程序页面路径
        miniProgramObj.withShareTicket = call.argument("withShareTicket") ?: true

        val msg = WXMediaMessage(miniProgramObj)
        msg.title = call.argument(WechatPluginKeys.TITLE)   // 小程序消息title
        msg.description = call.argument("description") // 小程序消息desc

        val byteArray: ByteArray? = call.argument<ByteArray>(WechatPluginKeys.THUMBNAIL_DATA)

        val thumbnailBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
        val compressedByteArray = WeChatThumbnailUtil.bmpToByteArray(thumbnailBitmap, 64)

        GlobalScope.launch((Dispatchers.Main), CoroutineStart.DEFAULT) {

            msg.thumbData = compressedByteArray

            val req = SendMessageToWX.Req()
            setCommonArguments(call, req, msg)
            req.message = msg
            val done = WXAPiHandler.wxApi?.sendReq(req)
            result.success(
                    mapOf(
                            WechatPluginKeys.PLATFORM to WechatPluginKeys.ANDROID,
                            WechatPluginKeys.RESULT to done
                    )
            )
        }
    }

    private suspend fun getThumbnailByteArrayMiniProgram(registrar: PluginRegistry.Registrar?, thumbnail: String): ByteArray {

        return GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT) {
            val result = WeChatThumbnailUtil.thumbnailForMiniProgram(thumbnail, registrar)
            result ?: byteArrayOf()
        }.await()
    }

    private suspend fun getImageByteArrayCommon(registrar: PluginRegistry.Registrar?, imagePath: String): ByteArray {
        return GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT) {
            val result = ShareImageUtil.getImageData(registrar, imagePath)
            result ?: byteArrayOf()
        }.await()
    }

    private suspend fun getThumbnailByteArrayCommon(registrar: PluginRegistry.Registrar?, thumbnail: String): ByteArray {
        return GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT) {
            val result = WeChatThumbnailUtil.thumbnailForCommon(thumbnail, registrar)
            result ?: byteArrayOf()
        }.await()
    }

    private fun shareImage(call: MethodCall, result: MethodChannel.Result) {
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) {

            val byteArray: ByteArray? = call.argument<ByteArray>(WechatPluginKeys.DATA)

            val originalBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
            val compressedByteArray = WeChatThumbnailUtil.bmpToByteArray(originalBitmap, 128)

            val imgObj = if (byteArray != null && byteArray.isNotEmpty()) {
                WXImageObject(compressedByteArray)
            } else {
                null
            }

            if (imgObj == null) {
                result.error(CallResult.RESULT_FILE_NOT_EXIST, CallResult.RESULT_FILE_NOT_EXIST, null)
                return@launch
            }


            // 处理缩略图
            val thumbnailBitmap = BitmapFactory.decodeByteArray(compressedByteArray, 0, compressedByteArray!!.size)
            val thumbnailData = WeChatThumbnailUtil.compressByQuality(thumbnailBitmap, 32, true)

            handleShareImage(imgObj, call, thumbnailData, result)
        }

    }

    private fun handleShareImage(imgObj: WXImageObject, call: MethodCall, thumbnailData: ByteArray?, result: MethodChannel.Result) {

        val msg = WXMediaMessage()
        msg.mediaObject = imgObj
        if (thumbnailData == null || thumbnailData.isEmpty()) {
            msg.thumbData = null
        } else {
            msg.thumbData = thumbnailData
        }

        msg.title = call.argument<String>(WechatPluginKeys.TITLE)
        msg.description = call.argument<String>(WechatPluginKeys.DESCRIPTION)

        val req = SendMessageToWX.Req()
        setCommonArguments(call, req, msg)
        req.message = msg
        val done = WXAPiHandler.wxApi?.sendReq(req)
        result.success(
                mapOf(
                        WechatPluginKeys.PLATFORM to WechatPluginKeys.ANDROID,
                        WechatPluginKeys.RESULT to done
                )
        )
    }

    private fun shareMusic(call: MethodCall, result: MethodChannel.Result) {
        val music = WXMusicObject()
        val musicUrl: String? = call.argument("musicUrl")
        val musicLowBandUrl: String? = call.argument("musicLowBandUrl")
        if (musicUrl != null && musicUrl.isNotBlank()) {
            music.musicUrl = musicUrl
            music.musicDataUrl = call.argument("musicDataUrl")
        } else {
            music.musicLowBandUrl = musicLowBandUrl
            music.musicLowBandDataUrl = call.argument("musicLowBandDataUrl")
        }
        val msg = WXMediaMessage()
        msg.mediaObject = music
        msg.title = call.argument("title")
        msg.description = call.argument("description")
        val thumbnail: String? = call.argument("thumbnail")

        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) {
            if (thumbnail != null && thumbnail.isNotBlank()) {
                msg.thumbData = getThumbnailByteArrayCommon(registrar, thumbnail)
            }

            val req = SendMessageToWX.Req()
            setCommonArguments(call, req, msg)
            req.message = msg
            val done = WXAPiHandler.wxApi?.sendReq(req)
            result.success(
                    mapOf(
                            WechatPluginKeys.PLATFORM to WechatPluginKeys.ANDROID,
                            WechatPluginKeys.RESULT to done
                    )
            )
        }
    }

    private fun shareVideo(call: MethodCall, result: MethodChannel.Result) {
        val video = WXVideoObject()
        val videoUrl: String? = call.argument("videoUrl")
        val videoLowBandUrl: String? = call.argument("videoLowBandUrl")
        if (videoUrl != null && videoUrl.isNotBlank()) {
            video.videoUrl = videoUrl
        } else {
            video.videoLowBandUrl = videoLowBandUrl
        }
        val msg = WXMediaMessage()
        msg.mediaObject = video
        msg.title = call.argument(WechatPluginKeys.TITLE)
        msg.description = call.argument(WechatPluginKeys.DESCRIPTION)
        val thumbnail: String? = call.argument(WechatPluginKeys.THUMBNAIL)

        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) {
            if (thumbnail != null && thumbnail.isNotBlank()) {
                msg.thumbData = getThumbnailByteArrayCommon(registrar, thumbnail)
            }
            val req = SendMessageToWX.Req()
            setCommonArguments(call, req, msg)
            req.message = msg
            val done = WXAPiHandler.wxApi?.sendReq(req)
            result.success(
                    mapOf(
                            WechatPluginKeys.PLATFORM to WechatPluginKeys.ANDROID,
                            WechatPluginKeys.RESULT to done
                    )
            )
        }
    }


    private fun shareWebPage(call: MethodCall, result: MethodChannel.Result) {
        val webPage = WXWebpageObject()
        webPage.webpageUrl = call.argument("webPage")
        val msg = WXMediaMessage()

        msg.mediaObject = webPage
        msg.title = call.argument(WechatPluginKeys.TITLE)
        msg.description = call.argument(WechatPluginKeys.DESCRIPTION)
        val thumbnail: String? = call.argument(WechatPluginKeys.THUMBNAIL)
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) {
            if (thumbnail != null && thumbnail.isNotBlank()) {
                msg.thumbData = getThumbnailByteArrayCommon(registrar, thumbnail)
            }
            val req = SendMessageToWX.Req()
            setCommonArguments(call, req, msg)
            req.message = msg
            val done = WXAPiHandler.wxApi?.sendReq(req)
            result.success(
                    mapOf(
                            WechatPluginKeys.PLATFORM to WechatPluginKeys.ANDROID,
                            WechatPluginKeys.RESULT to done
                    )
            )
        }
    }

    private fun getScene(value: String) = when (value) {
        WechatPluginKeys.SCENE_TIMELINE -> SendMessageToWX.Req.WXSceneTimeline
        WechatPluginKeys.SCENE_SESSION -> SendMessageToWX.Req.WXSceneSession
        WechatPluginKeys.SCENE_FAVORITE -> SendMessageToWX.Req.WXSceneFavorite
        else -> SendMessageToWX.Req.WXSceneTimeline
    }

    private fun setCommonArguments(call: MethodCall, req: SendMessageToWX.Req, msg: WXMediaMessage) {
        msg.messageAction = call.argument<String>(WechatPluginKeys.MESSAGE_ACTION)
        msg.messageExt = call.argument<String>(WechatPluginKeys.MESSAGE_EXT)
        msg.mediaTagName = call.argument<String>(WechatPluginKeys.MEDIA_TAG_NAME)
        req.transaction = call.argument(WechatPluginKeys.TRANSACTION)
        req.scene = getScene(call.argument(WechatPluginKeys.SCENE)
                ?: WechatPluginKeys.SCENE_SESSION)
    }

}