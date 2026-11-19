import CarPlay
import ImageIO
import NaviampShared
import UIKit

/// CarPlay scene/template boundary. Core supplies content, navigation requests and readiness policy.
@MainActor
final class NaviampCarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate,
    @MainActor CPSearchTemplateDelegate {
    private var application: NaviampIosApplication?
    private var interfaceController: CPInterfaceController?
    private var browseSession: NaviampIosVehicleBrowseSession?
    private var latestState: NaviampIosVehicleBrowseState?
    private var rootTemplate: CPTabBarTemplate?
    private var listTemplates: [String: CPListTemplate] = [:]
    private var pageTemplates: [String: CPTemplate] = [:]
    private final class SearchRow: NSObject {
        let pageId: String
        let rowId: String
        let revision: Int64
        init(pageId: String, rowId: String, revision: Int64) {
            self.pageId = pageId
            self.rowId = rowId
            self.revision = revision
            super.init()
        }
    }
    private var navigation: NaviampCarPlayTemplateNavigation?
    private var operationInFlight: Bool {
        get { navigation?.operationInFlight ?? false }
        set { navigation?.operationInFlight = newValue }
    }
    private var appliedPlaybackRevision: Int64 = -1
    private var playbackAlert: CPAlertTemplate?
    private var playbackAlertId: Int64?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        releasePresentation()
        let application = NaviampApplicationDelegate.runtime.application()
        self.application = application
        self.interfaceController = interfaceController
        let navigation = NaviampCarPlayTemplateNavigation(interface: interfaceController)
        self.navigation = navigation
        navigation.onStackChanged = { [weak self] in
            if let self, let latest = self.latestState { self.render(latest) }
        }
        navigation.onRootRejected = { [weak self] in self?.browseSession?.rootPresentationFailed() }
        navigation.onNavigationRejected = { [weak self] pageId in
            self?.browseSession?.presentationFailed(pageId: pageId)
        }
        navigation.onBack = { [weak self] ids in
            guard let self else { return }
            for id in ids {
                (self.pageTemplates.removeValue(forKey: id) as? CPSearchTemplate)?.delegate = nil
            }
            self.browseSession?.dismissPages(ids: ids)
        }
        navigation.onUpNext = { [weak self] in self?.browseSession?.openUpNext() }
        navigation.onAlbumArtist = { [weak self] in
            guard let self, let state = self.latestState else { return }
            self.browseSession?.openCurrentAlbum(revision: state.revision)
        }
        interfaceController.delegate = navigation
        CPNowPlayingTemplate.shared.add(navigation)
        let supportsSearch: Bool
        if #available(iOS 27.0, *) { supportsSearch = true } else { supportsSearch = false }
        browseSession = application.vehicleBrowseSession(
            maximumItems: Int32(CPListTemplate.maximumItemCount),
            maximumTabs: Int32(CPTabBarTemplate.maximumTabCount),
            supportsSearch: supportsSearch
        ) { [weak self, weak navigation] state in
            guard let self, let navigation, self.navigation === navigation else { return }
            self.render(state)
        }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        guard self.interfaceController === interfaceController else { return }
        releasePresentation()
    }

    private func releasePresentation() {
        if let navigation {
            navigation.invalidate()
            CPNowPlayingTemplate.shared.remove(navigation)
        }
        navigation = nil
        CPNowPlayingTemplate.shared.updateNowPlayingButtons([])
        CPNowPlayingTemplate.shared.isUpNextButtonEnabled = false
        CPNowPlayingTemplate.shared.isAlbumArtistButtonEnabled = false
        interfaceController?.delegate = nil
        pageTemplates.values.forEach { ($0 as? CPSearchTemplate)?.delegate = nil }
        browseSession?.close()
        browseSession = nil
        interfaceController = nil
        application = nil
        latestState = nil
        rootTemplate = nil
        listTemplates.removeAll()
        pageTemplates.removeAll()
        appliedPlaybackRevision = -1
        playbackAlert = nil
        playbackAlertId = nil
    }

    /// CPListTemplate/CPListItem translation and native handler completion only.
    private func update(
        _ template: CPListTemplate, title: String, status: String, rows: [NaviampIosVehicleBrowseRow],
        revision: Int64, destination: String?, pageId: String?
    ) {
        let items = rows.map { row -> CPListItem in
            let item = CPListItem(text: row.title, detailText: row.subtitle)
            item.isEnabled = row.enabled
            applyArtwork(row, to: item)
            item.handler = { [weak self, weak interfaceController, weak browseSession] _, completion in
                guard let self, let session = browseSession, let interfaceController,
                      self.interfaceController === interfaceController, self.browseSession === session else {
                    completion()
                    return
                }
                if let pageId {
                    session.selectPage(pageId: pageId, id: row.id, revision: revision) { _ in completion() }
                } else if let destination {
                    session.select(destination: destination, id: row.id, revision: revision) { _ in completion() }
                } else { completion() }
            }
            return item
        }
        template.emptyViewTitleVariants = [status.isEmpty ? title : status]
        template.updateSections(items.isEmpty ? [] : [CPListSection(
            items: items, header: status.isEmpty ? nil : status, sectionIndexTitle: nil
        )])
    }

    /// UIKit/ImageIO decoding, aspect-fill clipping and CPListItem image effects only.
    private func applyArtwork(_ row: NaviampIosVehicleBrowseRow, to item: CPListItem) {
        guard let id = row.artworkId, let session = browseSession else { return }
        let side = min(CPListItem.maximumImageSize.width, CPListItem.maximumImageSize.height)
        let scale = max(1, interfaceController?.carTraitCollection.displayScale ?? 1)
        let placeholder = renderArtwork(nil, placeholder: row.artworkPlaceholder,
                                        circular: row.artworkCircular, side: side, scale: scale)
        item.setImage(placeholder)
        session.requestArtwork(id: id) { [weak self, weak session, weak item] data in
            guard let self, let session, let item, self.browseSession === session, let data else { return }
            item.setImage(self.renderArtwork(data, placeholder: row.artworkPlaceholder,
                                            circular: row.artworkCircular, side: side, scale: scale))
        }
    }

    private func renderArtwork(
        _ data: Data?, placeholder: String?, circular: Bool, side: CGFloat, scale: CGFloat
    ) -> UIImage? {
        guard side > 0 else { return nil }
        var image: UIImage?
        if let data, let source = CGImageSourceCreateWithData(data as CFData, nil),
           let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: Int(ceil(side * scale)),
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true
           ] as CFDictionary) {
            image = UIImage(cgImage: thumbnail)
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let bounds = CGRect(x: 0, y: 0, width: side, height: side)
        return UIGraphicsImageRenderer(size: bounds.size, format: format).image { context in
            if circular { UIBezierPath(ovalIn: bounds).addClip() }
            if let image, image.size.width > 0, image.size.height > 0 {
                let factor = max(side / image.size.width, side / image.size.height)
                let size = CGSize(width: image.size.width * factor, height: image.size.height * factor)
                image.draw(in: CGRect(x: (side - size.width) / 2, y: (side - size.height) / 2,
                                      width: size.width, height: size.height))
            } else {
                UIColor.secondarySystemBackground.setFill()
                context.fill(bounds)
                let symbol: String
                switch placeholder {
                case "Artist": symbol = "person.fill"
                case "Album": symbol = "square.stack.fill"
                case "Playlist": symbol = "music.note.list"
                case "Radio": symbol = "radio.fill"
                default: symbol = "music.note"
                }
                UIImage(systemName: symbol)?.withTintColor(.secondaryLabel, renderingMode: .alwaysOriginal)
                    .draw(in: bounds.insetBy(dx: side * 0.25, dy: side * 0.25))
            }
        }
    }

    func searchTemplate(
        _ searchTemplate: CPSearchTemplate, updatedSearchText searchText: String,
        completionHandler: @escaping ([CPListItem]) -> Void
    ) {
        guard let session = browseSession,
              let pageId = pageTemplates.first(where: { $0.value === searchTemplate })?.key else {
            completionHandler([])
            return
        }
        session.updateSearch(pageId: pageId, text: searchText) { [weak self, weak interfaceController, weak session, weak searchTemplate] result in
            guard let self, let interfaceController, let session, let searchTemplate,
                  self.interfaceController === interfaceController, self.browseSession === session,
                  self.pageTemplates[pageId] === searchTemplate else {
                completionHandler([])
                return
            }
            completionHandler(result.rows.map { row in
                let item = CPListItem(text: row.title, detailText: row.subtitle)
                item.isEnabled = row.enabled
                self.applyArtwork(row, to: item)
                item.userInfo = SearchRow(pageId: pageId, rowId: row.id, revision: result.revision)
                return item
            })
        }
    }

    func searchTemplate(
        _ searchTemplate: CPSearchTemplate, selectedResult item: CPListItem,
        completionHandler: @escaping () -> Void
    ) {
        guard let session = browseSession, let row = item.userInfo as? SearchRow,
              pageTemplates[row.pageId] === searchTemplate else {
            completionHandler()
            return
        }
        session.selectSearch(pageId: row.pageId, id: row.rowId, revision: row.revision) { _ in completionHandler() }
    }

    func searchTemplateSearchButtonPressed(_ searchTemplate: CPSearchTemplate) {
        guard let pageId = pageTemplates.first(where: { $0.value === searchTemplate })?.key else { return }
        browseSession?.submitSearch(pageId: pageId)
    }

    /// Map Core button descriptors to the irreducible CarPlay SDK classes and callbacks.
    private func updatePlaybackButtons(_ state: NaviampIosVehiclePlaybackState) {
        guard appliedPlaybackRevision != state.revision else { return }
        appliedPlaybackRevision = state.revision
        let buttons: [CPNowPlayingButton] = state.buttons.compactMap { descriptor in
            let handler: (CPNowPlayingButton) -> Void = { [weak self, weak interfaceController, weak browseSession] _ in
                guard let self, let interfaceController, let session = browseSession,
                      self.interfaceController === interfaceController, self.browseSession === session else { return }
                session.selectPlaybackAction(action: descriptor.action, revision: state.revision)
            }
            let button: CPNowPlayingButton
            switch descriptor.action {
            case "ShuffleOn", "ShuffleOff": button = CPNowPlayingShuffleButton(handler: handler)
            case "RepeatOff", "RepeatQueue", "RepeatTrack": button = CPNowPlayingRepeatButton(handler: handler)
            case "Favorite", "Unfavorite", "StartRadio":
                let symbol = descriptor.action == "StartRadio" ? "dot.radiowaves.left.and.right"
                    : descriptor.selected ? "heart.fill" : "heart"
                guard let image = UIImage(systemName: symbol) else { return nil }
                button = CPNowPlayingImageButton(image: image, handler: handler)
                button.isSelected = descriptor.selected
            default: return nil
            }
            button.isEnabled = descriptor.enabled
            button.accessibilityLabel = descriptor.title
            return button
        }
        CPNowPlayingTemplate.shared.updateNowPlayingButtons(buttons)
    }

    /// Serialize native modal effects with stack effects. Core owns alert creation and dismissal.
    private func renderPlaybackAlert(_ state: NaviampIosVehiclePlaybackState) -> Bool {
        guard let interfaceController, let session = browseSession else { return false }
        if let existing = playbackAlert, playbackAlertId != state.alert?.id {
            let oldId = playbackAlertId
            playbackAlert = nil
            playbackAlertId = nil
            guard interfaceController.presentedTemplate === existing else { return false }
            operationInFlight = true
            interfaceController.dismissTemplate(animated: true) { [weak self, weak interfaceController, weak session] success, _ in
                guard let self, let interfaceController, let session,
                      self.interfaceController === interfaceController, self.browseSession === session else { return }
                self.operationInFlight = false
                if !success, let oldId { session.dismissPlaybackAlert(id: oldId) }
                if let latest = self.latestState { self.render(latest) }
            }
            return true
        }
        guard let alert = state.alert, playbackAlertId != alert.id else { return playbackAlert != nil }
        guard interfaceController.presentedTemplate == nil else {
            session.dismissPlaybackAlert(id: alert.id)
            return true
        }
        let action = CPAlertAction(title: alert.dismiss, style: .cancel) { [weak self, weak interfaceController, weak session] _ in
            guard let self, let interfaceController, let session,
                  self.interfaceController === interfaceController, self.browseSession === session else { return }
            session.dismissPlaybackAlert(id: alert.id)
        }
        let template = CPAlertTemplate(titleVariants: [alert.title], actions: [action])
        playbackAlert = template
        playbackAlertId = alert.id
        operationInFlight = true
        interfaceController.presentTemplate(template, animated: true) { [weak self, weak interfaceController, weak session] success, _ in
            guard let self, let interfaceController, let session,
                  self.interfaceController === interfaceController, self.browseSession === session else { return }
            self.operationInFlight = false
            if !success {
                self.playbackAlert = nil
                self.playbackAlertId = nil
                session.dismissPlaybackAlert(id: alert.id)
            }
            if let latest = self.latestState { self.render(latest) }
        }
        return true
    }

    private func render(_ state: NaviampIosVehicleBrowseState) {
        guard let navigation, let session = browseSession else { return }
        latestState = state
        updatePlaybackButtons(state.playback)
        CPNowPlayingTemplate.shared.isUpNextButtonEnabled = state.queueAvailable
        CPNowPlayingTemplate.shared.isAlbumArtistButtonEnabled = state.currentAlbumAvailable
        let templates = state.tabs.map { tab -> CPListTemplate in
            let template = listTemplates[tab.id] ?? CPListTemplate(title: tab.title, sections: [])
            update(template, title: tab.title, status: tab.status, rows: tab.rows,
                   revision: state.revision, destination: tab.id, pageId: nil)
            switch tab.id {
            case "Home": template.tabImage = UIImage(systemName: "house")
            case "Playlists": template.tabImage = UIImage(systemName: "music.note.list")
            case "Radio": template.tabImage = UIImage(systemName: "dot.radiowaves.left.and.right")
            case "Library": template.tabImage = UIImage(systemName: "books.vertical")
            case "Downloads": template.tabImage = UIImage(systemName: "arrow.down.circle")
            default: break
            }
            listTemplates[tab.id] = template
            return template
        }
        let desiredIds = Set(state.pages.map { $0.id })
        for id in pageTemplates.keys.filter({ !desiredIds.contains($0) && !navigation.retainsPage($0) }) {
            (pageTemplates[id] as? CPSearchTemplate)?.delegate = nil
            pageTemplates.removeValue(forKey: id)
        }
        for page in state.pages {
            if page.search {
                if #available(iOS 27.0, *) {
                    let template = (pageTemplates[page.id] as? CPSearchTemplate) ?? CPSearchTemplate()
                    template.delegate = self
                    pageTemplates[page.id] = template
                } else {
                    session.presentationFailed(pageId: page.id)
                    return
                }
            } else {
                let template = (pageTemplates[page.id] as? CPListTemplate) ?? CPListTemplate(title: page.title, sections: [])
                update(template, title: page.title, status: page.status, rows: page.rows,
                       revision: state.revision, destination: nil, pageId: page.id)
                pageTemplates[page.id] = template
            }
        }
        guard !operationInFlight, state.presentationAvailable else { return }
        if rootTemplate == nil {
            guard !templates.isEmpty else { return }
            rootTemplate = CPTabBarTemplate(templates: templates)
        }
        guard let rootTemplate else { return }
        if !navigation.hasInstalledRoot {
            let revision = state.pages.isEmpty && !state.nowPlaying ? state.navigationRevision : nil
            navigation.installRoot(rootTemplate, appliedRevision: revision)
            return
        }
        if renderPlaybackAlert(state.playback) { return }
        var desired: [CPTemplate] = [rootTemplate]
        for page in state.pages {
            if state.nowPlayingBeforePageId == page.id {
                desired.append(CPNowPlayingTemplate.shared)
            }
            if let template = pageTemplates[page.id] {
                desired.append(template)
            }
        }
        if state.nowPlaying && state.nowPlayingBeforePageId == nil {
            desired.append(CPNowPlayingTemplate.shared)
        }
        navigation.reconcile(path: desired, pages: pageTemplates, revision: state.navigationRevision)
    }
}
