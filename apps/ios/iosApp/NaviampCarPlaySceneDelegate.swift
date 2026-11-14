import CarPlay
import NaviampShared
import UIKit

/// CarPlay scene and template boundary. Playback stays in the application's existing Core runtime.
@MainActor
final class NaviampCarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate {
    private var application: NaviampIosApplication?
    private var interfaceController: CPInterfaceController?
    private var browseSession: NaviampIosVehicleBrowseSession?
    private var listTemplates: [String: CPListTemplate] = [:]

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        browseSession?.close()
        listTemplates.removeAll()
        let application = NaviampApplicationDelegate.runtime.application()
        self.application = application
        self.interfaceController = interfaceController
        browseSession = application.vehicleBrowseSession(
            maximumItems: Int32(CPListTemplate.maximumItemCount),
            maximumTabs: Int32(CPTabBarTemplate.maximumTabCount)
        ) { [weak self] revision, tabs in
            self?.render(revision: revision.int64Value, tabs: tabs)
        }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        guard self.interfaceController === interfaceController else { return }
        browseSession?.close()
        browseSession = nil
        listTemplates.removeAll()
        self.interfaceController = nil
        application = nil
    }

    /// UIKit/CarPlay types, template updates and native selection-completion callbacks only.
    private func render(revision: Int64, tabs: [NaviampIosVehicleBrowseTab]) {
        guard let interfaceController, let session = browseSession else { return }
        let installingRoot = listTemplates.isEmpty
        let templates = tabs.map { tab -> CPListTemplate in
            let template = listTemplates[tab.id] ?? CPListTemplate(title: tab.title, sections: [])
            let rows = tab.rows.map { row -> CPListItem in
                let item = CPListItem(text: row.title, detailText: row.subtitle)
                item.handler = { [weak self, weak interfaceController, weak session] _, completion in
                    guard let self, let session, let interfaceController,
                          self.interfaceController === interfaceController,
                          self.browseSession === session else {
                        completion()
                        return
                    }
                    session.select(destination: tab.id, id: row.id, revision: revision) { [weak self, weak interfaceController] ready in
                        completion()
                        guard ready.boolValue, let interfaceController,
                              self?.interfaceController === interfaceController else { return }
                        if !(interfaceController.topTemplate is CPNowPlayingTemplate) {
                            interfaceController.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
                        }
                    }
                }
                return item
            }
            template.emptyViewTitleVariants = [tab.status.isEmpty ? tab.title : tab.status]
            template.updateSections(rows.isEmpty ? [] : [CPListSection(
                items: rows, header: tab.status.isEmpty ? nil : tab.status, sectionIndexTitle: nil
            )])
            switch tab.id {
            case "Home": template.tabImage = UIImage(systemName: "house")
            case "Playlists": template.tabImage = UIImage(systemName: "music.note.list")
            case "Radio": template.tabImage = UIImage(systemName: "dot.radiowaves.left.and.right")
            default: break
            }
            listTemplates[tab.id] = template
            return template
        }
        if installingRoot && !templates.isEmpty {
            interfaceController.setRootTemplate(CPTabBarTemplate(templates: templates), animated: false, completion: nil)
        }
    }
}
