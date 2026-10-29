import UIKit

@main final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Main", sessionRole: session.role); config.delegateClass = SceneDelegate.self; return config
    }
}
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let w = UIWindow(windowScene: scene); w.overrideUserInterfaceStyle = .dark
        w.rootViewController = DashboardController(); window = w; w.makeKeyAndVisible()
    }
    func sceneWillResignActive(_ scene: UIScene) { (window?.rootViewController as? DashboardController)?.endSession("Déconnecté : application inactive.") }
    func sceneDidEnterBackground(_ scene: UIScene) { (window?.rootViewController as? DashboardController)?.endSession("Déconnecté en arrière-plan.") }
    func sceneDidDisconnect(_ scene: UIScene) { (window?.rootViewController as? DashboardController)?.endSession("Session terminée.") }
}
