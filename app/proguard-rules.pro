# The root helper is launched by fully-qualified class name via app_process, and its binder is an AIDL stub
# reached reflectively. R8 must not rename or strip any of it.
-keep class jr.brian.commandpannel.privileged.helper.RootHelperMain { public static void main(java.lang.String[]); }
-keep class jr.brian.commandpannel.privileged.helper.RootHelperService { *; }
-keep class jr.brian.commandpannel.privileged.helper.IRootHelper { *; }
-keep class jr.brian.commandpannel.privileged.helper.IRootHelper$Stub { *; }
-keep class jr.brian.commandpannel.privileged.helper.IRootHelper$Stub$Proxy { *; }
-keep class jr.brian.commandpannel.privileged.helper.HelperProtocol { *; }

# Hidden framework classes reached by reflection in the privileged layer.
-keep class android.os.ServiceManager { *; }
-keep class android.hardware.display.DisplayManagerGlobal { *; }

# Our copy of the framework's hidden ITaskFpsCallback (TaskFps) must keep its real name: the framework class loads in
# its place in the helper, and registerTaskFpsCallback is looked up by that parameter type.
-keep class android.window.ITaskFpsCallback { *; }
-keep class android.window.ITaskFpsCallback$Stub { *; }
