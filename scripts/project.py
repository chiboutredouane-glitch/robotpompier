#!/usr/bin/env python3
"""Generate the committed native Xcode project without third-party dependencies."""
from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
objects={}
def ident(name):return hashlib.sha1(name.encode()).hexdigest()[:24].upper()
def obj(key_name,**fields):
 key=ident(key_name);objects[key]=fields;return key
def quote(s):return json.dumps(str(s),ensure_ascii=False)
def dump(x):
 if isinstance(x,dict):return '{ '+ ' '.join(quote(k)+' = '+dump(v)+';' for k,v in x.items())+' }'
 if isinstance(x,list):return '('+', '.join(dump(v) for v in x)+')'
 return quote(x)
appfiles=sorted([p.relative_to(root).as_posix() for d in ['App','Core'] for p in (root/d).glob('*.swift')])
testfiles=['AppTests/AppTests.swift']
refs={}
for p in appfiles+testfiles+['App/Info.plist','App/PrivacyInfo.xcprivacy']:
 refs[p]=obj('file:'+p,isa='PBXFileReference',lastKnownFileType='sourcecode.swift' if p.endswith('.swift') else 'text.plist.xml',path=p,sourceTree='<group>')
assets=obj('assets',isa='PBXFileReference',lastKnownFileType='folder.assetcatalog',path='App/Assets.xcassets',sourceTree='<group>')
appProduct=obj('appProduct',isa='PBXFileReference',explicitFileType='wrapper.application',path='TelecommandeUniverselle.app',sourceTree='BUILT_PRODUCTS_DIR')
testProduct=obj('testProduct',isa='PBXFileReference',explicitFileType='wrapper.cfbundle',path='TelecommandeUniverselleTests.xctest',sourceTree='BUILT_PRODUCTS_DIR')
products=obj('products',isa='PBXGroup',children=[appProduct,testProduct],name='Products',sourceTree='<group>')
mainGroup=obj('mainGroup',isa='PBXGroup',children=list(refs.values())+[assets,products],sourceTree='<group>')
def phase(name,kind,paths):
 builds=[obj(name+':'+p,isa='PBXBuildFile',fileRef=refs[p]) for p in paths]
 return obj(name,isa=kind,buildActionMask='2147483647',files=builds,runOnlyForDeploymentPostprocessing='0')
appSources=phase('appSources','PBXSourcesBuildPhase',appfiles)
testSources=phase('testSources','PBXSourcesBuildPhase',testfiles)
assetBuild=obj('assetBuild',isa='PBXBuildFile',fileRef=assets)
privacyBuild=obj('privacyBuild',isa='PBXBuildFile',fileRef=refs['App/PrivacyInfo.xcprivacy'])
resources=obj('resources',isa='PBXResourcesBuildPhase',buildActionMask='2147483647',files=[assetBuild,privacyBuild],runOnlyForDeploymentPostprocessing='0')
frameworks=obj('frameworks',isa='PBXFrameworksBuildPhase',buildActionMask='2147483647',files=[],runOnlyForDeploymentPostprocessing='0')
base={'IPHONEOS_DEPLOYMENT_TARGET':'16.0','SDKROOT':'iphoneos','SWIFT_VERSION':'5.0','SWIFT_STRICT_CONCURRENCY':'minimal','CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','ENABLE_USER_SCRIPT_SANDBOXING':'YES','GCC_C_LANGUAGE_STANDARD':'gnu17','TARGETED_DEVICE_FAMILY':'1,2','CODE_SIGN_STYLE':'Automatic'}
def configs(name,settings):
 cs=[]
 for config in ['Debug','Release']:
  bs=dict(settings)
  bs.update({'SWIFT_OPTIMIZATION_LEVEL':'-Onone' if config=='Debug' else '-O','DEBUG_INFORMATION_FORMAT':'dwarf' if config=='Debug' else 'dwarf-with-dsym','SWIFT_ACTIVE_COMPILATION_CONDITIONS':'DEBUG' if config=='Debug' else ''})
  if config=='Debug':bs['ENABLE_TESTABILITY']='YES'
  cs.append(obj(name+config,isa='XCBuildConfiguration',buildSettings=bs,name=config))
 return obj(name+'configlist',isa='XCConfigurationList',buildConfigurations=cs,defaultConfigurationIsVisible='0',defaultConfigurationName='Release')
projConfigs=configs('project',base)
appConfigs=configs('app',{'PRODUCT_BUNDLE_IDENTIFIER':'fr.telecommande.universelle.ios','PRODUCT_NAME':'TelecommandeUniverselle','PRODUCT_MODULE_NAME':'TelecommandeUniverselle','INFOPLIST_FILE':'App/Info.plist','GENERATE_INFOPLIST_FILE':'NO','ASSETCATALOG_COMPILER_APPICON_NAME':'AppIcon','MARKETING_VERSION':'1.0.3','CURRENT_PROJECT_VERSION':'1','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks']})
appTarget=ident('appTarget');project=ident('project')
proxy=obj('testProxy',isa='PBXContainerItemProxy',containerPortal=project,proxyType='1',remoteGlobalIDString=appTarget,remoteInfo='TelecommandeUniverselle')
dep=obj('testDep',isa='PBXTargetDependency',target=appTarget,targetProxy=proxy)
testConfigs=configs('test',{'PRODUCT_BUNDLE_IDENTIFIER':'fr.telecommande.universelle.ios.tests','PRODUCT_NAME':'TelecommandeUniverselleTests','GENERATE_INFOPLIST_FILE':'YES','TEST_HOST':'$(BUILT_PRODUCTS_DIR)/TelecommandeUniverselle.app/TelecommandeUniverselle','BUNDLE_LOADER':'$(TEST_HOST)','LD_RUNPATH_SEARCH_PATHS':['$(inherited)','@executable_path/Frameworks','@loader_path/Frameworks']})
obj('appTarget',isa='PBXNativeTarget',buildConfigurationList=appConfigs,buildPhases=[appSources,frameworks,resources],buildRules=[],dependencies=[],name='TelecommandeUniverselle',productName='TelecommandeUniverselle',productReference=appProduct,productType='com.apple.product-type.application')
testTarget=obj('testTarget',isa='PBXNativeTarget',buildConfigurationList=testConfigs,buildPhases=[testSources],buildRules=[],dependencies=[dep],name='TelecommandeUniverselleTests',productName='TelecommandeUniverselleTests',productReference=testProduct,productType='com.apple.product-type.bundle.unit-test')
obj('project',isa='PBXProject',attributes={'BuildIndependentTargetsInParallel':'YES','LastUpgradeCheck':'1600','TargetAttributes':{appTarget:{'CreatedOnToolsVersion':'16.0'},testTarget:{'CreatedOnToolsVersion':'16.0','TestTargetID':appTarget}}},buildConfigurationList=projConfigs,compatibilityVersion='Xcode 14.0',developmentRegion='fr',hasScannedForEncodings='0',knownRegions=['fr','en','Base'],mainGroup=mainGroup,productRefGroup=products,projectDirPath='',projectRoot='',targets=[appTarget,testTarget])
p=root/'TelecommandeUniverselle.xcodeproj';p.mkdir(exist_ok=True)
(p/'project.pbxproj').write_text('// !$*UTF8*$!\n'+dump({'archiveVersion':'1','classes':{},'objectVersion':'56','objects':objects,'rootObject':project})+'\n')
schemes=p/'xcshareddata/xcschemes';schemes.mkdir(parents=True,exist_ok=True)
def ref(target,name):return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="{name}" BlueprintName="{name.split(".")[0]}" ReferencedContainer="container:TelecommandeUniverselle.xcodeproj"/>'
(schemes/'TelecommandeUniverselle.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
<BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{ref(appTarget,'TelecommandeUniverselle.app')}</BuildActionEntry>
<BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO">{ref(testTarget,'TelecommandeUniverselleTests.xctest')}</BuildActionEntry>
</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{ref(testTarget,'TelecommandeUniverselleTests.xctest')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{ref(appTarget,'TelecommandeUniverselle.app')}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{ref(appTarget,'TelecommandeUniverselle.app')}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>''')
print('Projet Xcode généré :',p.name)
