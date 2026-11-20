package jr.brian.commandpannel.replay.studio

import androidx.core.content.FileProvider

/**
 * Shares Replay Studio clips (authority `${applicationId}.clips`, paths in `res/xml/clip_paths.xml`). A subclass only
 * so it can sit in the manifest next to the updater's plain [FileProvider].
 */
class ClipFileProvider : FileProvider()
