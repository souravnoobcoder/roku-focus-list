import UIKit
import SwiftUI
import RokuSample

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose owns the whole 1920x1080 canvas; tvOS's overscan-safe area is
        // exposed to it as WindowInsets rather than shrinking the view.
        ComposeView()
                .ignoresSafeArea()
    }
}
