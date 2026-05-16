use anyhow::{Context, Result};
use std::io::Write;
use std::process::{Child, Command, Stdio};

#[cfg(target_os = "windows")]
use std::os::windows::process::CommandExt;

#[cfg(target_os = "windows")]
const CREATE_NO_WINDOW: u32 = 0x08000000;

#[derive(Default)]
pub struct MpvPlaybackEngine {
    child: Option<Child>,
}

impl MpvPlaybackEngine {
    pub fn play_url(&mut self, url: &str) -> Result<()> {
        self.stop();
        let mut command = Command::new("mpv");
        command
            .arg("--no-video")
            .arg("--really-quiet")
            .arg("--idle=no")
            .arg(url)
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        configure_hidden_child_window(&mut command);
        let child = command.spawn().context("could not launch mpv")?;
        self.child = Some(child);
        Ok(())
    }

    pub fn stop(&mut self) {
        if let Some(mut child) = self.child.take() {
            if let Some(mut stdin) = child.stdin.take() {
                let _ = stdin.write_all(b"q\n");
            }
            if child.try_wait().ok().flatten().is_none() {
                kill_process_tree(child.id());
            }
            if child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
            }
            let _ = child.wait();
        }
    }
}

#[cfg(target_os = "windows")]
fn configure_hidden_child_window(command: &mut Command) {
    command.creation_flags(CREATE_NO_WINDOW);
}

#[cfg(not(target_os = "windows"))]
fn configure_hidden_child_window(_command: &mut Command) {}

#[cfg(target_os = "windows")]
fn kill_process_tree(process_id: u32) {
    let mut command = Command::new("taskkill");
    command
        .arg("/PID")
        .arg(process_id.to_string())
        .arg("/T")
        .arg("/F")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    configure_hidden_child_window(&mut command);
    let _ = command.status();
}

#[cfg(not(target_os = "windows"))]
fn kill_process_tree(_process_id: u32) {}

impl Drop for MpvPlaybackEngine {
    fn drop(&mut self) {
        self.stop();
    }
}
