use std::sync::mpsc::{self, Sender};
use std::thread;

type Job = Box<dyn FnOnce() + Send + 'static>;

#[derive(Clone)]
pub struct BackgroundWorker {
    sender: Sender<Job>,
}

impl BackgroundWorker {
    pub fn new(name: &'static str) -> Self {
        let (sender, receiver) = mpsc::channel::<Job>();
        thread::Builder::new()
            .name(name.to_string())
            .spawn(move || {
                while let Ok(job) = receiver.recv() {
                    job();
                }
            })
            .expect("background worker thread should start");

        Self { sender }
    }

    pub fn submit<F>(&self, job: F)
    where
        F: FnOnce() + Send + 'static,
    {
        let _ = self.sender.send(Box::new(job));
    }
}
