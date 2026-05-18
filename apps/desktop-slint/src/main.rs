#![cfg_attr(
    all(target_os = "windows", not(debug_assertions)),
    windows_subsystem = "windows"
)]

mod app;
mod domain;
mod image_cache;
mod playback;
mod provider;
mod queue;
mod settings;
mod storage;
mod ui;
mod worker;

use anyhow::Result;

fn main() -> Result<()> {
    app::run()
}
