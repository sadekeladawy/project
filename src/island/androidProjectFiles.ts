import settingsGradle from '../../android-project/settings.gradle.kts?raw';
import rootBuildGradle from '../../android-project/build.gradle.kts?raw';
import gradleProperties from '../../android-project/gradle.properties?raw';
import libsVersionsToml from '../../android-project/gradle/libs.versions.toml?raw';
import gradleWrapperProps from '../../android-project/gradle/wrapper/gradle-wrapper.properties?raw';
import appBuildGradle from '../../android-project/app/build.gradle.kts?raw';
import proguardRules from '../../android-project/app/proguard-rules.pro?raw';
import androidManifest from '../../android-project/app/src/main/AndroidManifest.xml?raw';
import stringsXml from '../../android-project/app/src/main/res/values/strings.xml?raw';
import themesXml from '../../android-project/app/src/main/res/values/themes.xml?raw';
import colorsXml from '../../android-project/app/src/main/res/values/colors.xml?raw';
import icLauncherXml from '../../android-project/app/src/main/res/drawable/ic_island_launcher.xml?raw';
import icLauncherBgXml from '../../android-project/app/src/main/res/drawable/ic_island_launcher_bg.xml?raw';
import icLauncherFgXml from '../../android-project/app/src/main/res/drawable/ic_island_launcher_fg.xml?raw';
import icLauncherAdaptiveXml from '../../android-project/app/src/main/res/drawable-v26/ic_island_launcher.xml?raw';
import icNotifXml from '../../android-project/app/src/main/res/drawable/ic_island_notification.xml?raw';
import accessibilityConfigXml from '../../android-project/app/src/main/res/xml/accessibility_service_config.xml?raw';
import backupRulesXml from '../../android-project/app/src/main/res/xml/backup_rules.xml?raw';
import dataExtractionXml from '../../android-project/app/src/main/res/xml/data_extraction_rules.xml?raw';
import islandAppKt from '../../android-project/app/src/main/java/com/island/app/IslandApplication.kt?raw';
import mainActivityKt from '../../android-project/app/src/main/java/com/island/app/MainActivity.kt?raw';
import crashLoggerKt from '../../android-project/app/src/main/java/com/island/app/data/CrashLogger.kt?raw';
import islandSettingsKt from '../../android-project/app/src/main/java/com/island/app/data/IslandSettings.kt?raw';
import settingsRepoKt from '../../android-project/app/src/main/java/com/island/app/data/SettingsRepository.kt?raw';
import islandEventKt from '../../android-project/app/src/main/java/com/island/app/model/IslandEvent.kt?raw';
import islandStateMgrKt from '../../android-project/app/src/main/java/com/island/app/state/IslandStateManager.kt?raw';
import islandViewModelKt from '../../android-project/app/src/main/java/com/island/app/state/IslandViewModel.kt?raw';
import eventBusKt from '../../android-project/app/src/main/java/com/island/app/service/IslandEventBus.kt?raw';
import overlayServiceKt from '../../android-project/app/src/main/java/com/island/app/service/IslandOverlayService.kt?raw';
import mediaListenerKt from '../../android-project/app/src/main/java/com/island/app/service/MediaListenerService.kt?raw';
import accessibilityServiceKt from '../../android-project/app/src/main/java/com/island/app/service/IslandAccessibilityService.kt?raw';
import systemMonitorKt from '../../android-project/app/src/main/java/com/island/app/service/SystemEventMonitor.kt?raw';
import bootReceiverKt from '../../android-project/app/src/main/java/com/island/app/service/BootReceiver.kt?raw';
import dynamicIslandOverlayKt from '../../android-project/app/src/main/java/com/island/app/ui/overlay/DynamicIslandOverlay.kt?raw';
import appExitTransitionOverlayKt from '../../android-project/app/src/main/java/com/island/app/ui/overlay/AppExitTransitionOverlay.kt?raw';
import eventCardsKt from '../../android-project/app/src/main/java/com/island/app/ui/overlay/EventCards.kt?raw';
import settingsScreenKt from '../../android-project/app/src/main/java/com/island/app/ui/screens/SettingsScreen.kt?raw';
import themeKt from '../../android-project/app/src/main/java/com/island/app/ui/theme/Theme.kt?raw';

export interface AndroidProjectFile {
  path: string;
  name: string;
  category: 'Gradle & Manifest' | 'Services & State' | 'Compose UI' | 'Data & Resources';
  language: 'kotlin' | 'xml' | 'gradle' | 'properties';
  content: string;
}

export const ANDROID_PROJECT_FILES: AndroidProjectFile[] = [
  {
    path: 'app/src/main/AndroidManifest.xml',
    name: 'AndroidManifest.xml',
    category: 'Gradle & Manifest',
    language: 'xml',
    content: androidManifest,
  },
  {
    path: 'app/build.gradle.kts',
    name: 'app/build.gradle.kts',
    category: 'Gradle & Manifest',
    language: 'gradle',
    content: appBuildGradle,
  },
  {
    path: 'gradle/libs.versions.toml',
    name: 'libs.versions.toml',
    category: 'Gradle & Manifest',
    language: 'properties',
    content: libsVersionsToml,
  },
  {
    path: 'build.gradle.kts',
    name: 'build.gradle.kts',
    category: 'Gradle & Manifest',
    language: 'gradle',
    content: rootBuildGradle,
  },
  {
    path: 'settings.gradle.kts',
    name: 'settings.gradle.kts',
    category: 'Gradle & Manifest',
    language: 'gradle',
    content: settingsGradle,
  },
  {
    path: 'gradle.properties',
    name: 'gradle.properties',
    category: 'Gradle & Manifest',
    language: 'properties',
    content: gradleProperties,
  },
  {
    path: 'gradle/wrapper/gradle-wrapper.properties',
    name: 'gradle-wrapper.properties',
    category: 'Gradle & Manifest',
    language: 'properties',
    content: gradleWrapperProps,
  },
  {
    path: 'app/proguard-rules.pro',
    name: 'proguard-rules.pro',
    category: 'Gradle & Manifest',
    language: 'properties',
    content: proguardRules,
  },
  {
    path: 'app/src/main/java/com/island/app/service/MediaListenerService.kt',
    name: 'MediaListenerService.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: mediaListenerKt,
  },
  {
    path: 'app/src/main/java/com/island/app/service/IslandAccessibilityService.kt',
    name: 'IslandAccessibilityService.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: accessibilityServiceKt,
  },
  {
    path: 'app/src/main/java/com/island/app/service/IslandOverlayService.kt',
    name: 'IslandOverlayService.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: overlayServiceKt,
  },
  {
    path: 'app/src/main/java/com/island/app/state/IslandStateManager.kt',
    name: 'IslandStateManager.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: islandStateMgrKt,
  },
  {
    path: 'app/src/main/java/com/island/app/state/IslandViewModel.kt',
    name: 'IslandViewModel.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: islandViewModelKt,
  },
  {
    path: 'app/src/main/java/com/island/app/service/SystemEventMonitor.kt',
    name: 'SystemEventMonitor.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: systemMonitorKt,
  },
  {
    path: 'app/src/main/java/com/island/app/service/BootReceiver.kt',
    name: 'BootReceiver.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: bootReceiverKt,
  },
  {
    path: 'app/src/main/java/com/island/app/service/IslandEventBus.kt',
    name: 'IslandEventBus.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: eventBusKt,
  },
  {
    path: 'app/src/main/java/com/island/app/IslandApplication.kt',
    name: 'IslandApplication.kt',
    category: 'Services & State',
    language: 'kotlin',
    content: islandAppKt,
  },
  {
    path: 'app/src/main/java/com/island/app/ui/overlay/AppExitTransitionOverlay.kt',
    name: 'AppExitTransitionOverlay.kt',
    category: 'Compose UI',
    language: 'kotlin',
    content: appExitTransitionOverlayKt,
  },
  {
    path: 'app/src/main/java/com/island/app/ui/overlay/DynamicIslandOverlay.kt',
    name: 'DynamicIslandOverlay.kt',
    category: 'Compose UI',
    language: 'kotlin',
    content: dynamicIslandOverlayKt,
  },
  {
    path: 'app/src/main/java/com/island/app/ui/overlay/EventCards.kt',
    name: 'EventCards.kt',
    category: 'Compose UI',
    language: 'kotlin',
    content: eventCardsKt,
  },
  {
    path: 'app/src/main/java/com/island/app/ui/screens/SettingsScreen.kt',
    name: 'SettingsScreen.kt',
    category: 'Compose UI',
    language: 'kotlin',
    content: settingsScreenKt,
  },
  {
    path: 'app/src/main/java/com/island/app/ui/theme/Theme.kt',
    name: 'Theme.kt',
    category: 'Compose UI',
    language: 'kotlin',
    content: themeKt,
  },
  {
    path: 'app/src/main/java/com/island/app/MainActivity.kt',
    name: 'MainActivity.kt',
    category: 'Compose UI',
    language: 'kotlin',
    content: mainActivityKt,
  },
  {
    path: 'app/src/main/java/com/island/app/data/CrashLogger.kt',
    name: 'CrashLogger.kt',
    category: 'Data & Resources',
    language: 'kotlin',
    content: crashLoggerKt,
  },
  {
    path: 'app/src/main/java/com/island/app/data/IslandSettings.kt',
    name: 'IslandSettings.kt',
    category: 'Data & Resources',
    language: 'kotlin',
    content: islandSettingsKt,
  },
  {
    path: 'app/src/main/java/com/island/app/data/SettingsRepository.kt',
    name: 'SettingsRepository.kt',
    category: 'Data & Resources',
    language: 'kotlin',
    content: settingsRepoKt,
  },
  {
    path: 'app/src/main/java/com/island/app/model/IslandEvent.kt',
    name: 'IslandEvent.kt',
    category: 'Data & Resources',
    language: 'kotlin',
    content: islandEventKt,
  },
  {
    path: 'app/src/main/res/xml/accessibility_service_config.xml',
    name: 'accessibility_service_config.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: accessibilityConfigXml,
  },
  {
    path: 'app/src/main/res/values/strings.xml',
    name: 'strings.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: stringsXml,
  },
  {
    path: 'app/src/main/res/values/themes.xml',
    name: 'themes.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: themesXml,
  },
  {
    path: 'app/src/main/res/values/colors.xml',
    name: 'colors.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: colorsXml,
  },
  {
    path: 'app/src/main/res/drawable-v26/ic_island_launcher.xml',
    name: 'drawable-v26/ic_island_launcher.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: icLauncherAdaptiveXml,
  },
  {
    path: 'app/src/main/res/drawable/ic_island_launcher_bg.xml',
    name: 'ic_island_launcher_bg.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: icLauncherBgXml,
  },
  {
    path: 'app/src/main/res/drawable/ic_island_launcher_fg.xml',
    name: 'ic_island_launcher_fg.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: icLauncherFgXml,
  },
  {
    path: 'app/src/main/res/drawable/ic_island_launcher.xml',
    name: 'ic_island_launcher.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: icLauncherXml,
  },
  {
    path: 'app/src/main/res/drawable/ic_island_notification.xml',
    name: 'ic_island_notification.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: icNotifXml,
  },
  {
    path: 'app/src/main/res/xml/backup_rules.xml',
    name: 'backup_rules.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: backupRulesXml,
  },
  {
    path: 'app/src/main/res/xml/data_extraction_rules.xml',
    name: 'data_extraction_rules.xml',
    category: 'Data & Resources',
    language: 'xml',
    content: dataExtractionXml,
  },
];

export async function downloadAndroidProjectZip(): Promise<void> {
  const { default: JSZip } = await import('jszip');
  const zip = new JSZip();
  const root = zip.folder('Island');
  if (!root) return;

  for (const file of ANDROID_PROJECT_FILES) {
    root.file(file.path, file.content);
  }

  const blob = await zip.generateAsync({ type: 'blob' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'Island-Android-Project.zip';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
