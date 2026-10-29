// swift-tools-version: 5.9
import PackageDescription

let package = Package(name: "ControlCore", platforms: [.macOS(.v13), .iOS(.v16)],
    products: [.library(name: "ControlCore", targets: ["ControlCore"])],
    targets: [.target(name: "ControlCore", path: "Core"),
              .testTarget(name: "ControlCoreTests", dependencies: ["ControlCore"])])
