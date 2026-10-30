#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <tlhelp32.h>
#include <dbghelp.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <fstream>
#include <iostream>
#include <map>
#include <string>
#include <thread>
#include <vector>

struct Handle {
    HANDLE value;
    ~Handle() { if (value && value != INVALID_HANDLE_VALUE) CloseHandle(value); }
};
unsigned long long cpu(HANDLE thread, bool kernelOnly=false) {
    FILETIME created{}, ended{}, kernel{}, user{};
    if (!GetThreadTimes(thread, &created, &ended, &kernel, &user)) throw GetLastError();
    ULARGE_INTEGER k{}, u{};
    k.LowPart=kernel.dwLowDateTime; k.HighPart=kernel.dwHighDateTime;
    u.LowPart=user.dwLowDateTime; u.HighPart=user.dwHighDateTime;
    return k.QuadPart+(kernelOnly?0:u.QuadPart);
}
struct StackCopy { DWORD64 base=0; SIZE_T size=0; std::array<BYTE,65536> bytes{}; };
StackCopy stackCopy;
BOOL CALLBACK readSnapshot(HANDLE process, DWORD64 address, PVOID target, DWORD size, LPDWORD read) {
    if(address>=stackCopy.base && address-stackCopy.base+size<=stackCopy.size) {
        memcpy(target,stackCopy.bytes.data()+address-stackCopy.base,size); *read=size; return TRUE;
    }
    SIZE_T count=0;
    BOOL result=ReadProcessMemory(process,reinterpret_cast<LPCVOID>(address),target,size,&count);
    *read=static_cast<DWORD>(count); return result;
}
int main(int argc, char** argv) {
    try {
        if(argc!=5) { std::cerr << "Usage: sampler PID TID seconds output.txt\n"; return 2; }
        DWORD pid=std::stoul(argv[1]), tid=std::stoul(argv[2]);
        int seconds=std::stoi(argv[3]);
        if(seconds<1 || seconds>30) return 2;
        Handle process{OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ,FALSE,pid)};
        Handle thread{OpenThread(THREAD_GET_CONTEXT|THREAD_SUSPEND_RESUME|THREAD_QUERY_INFORMATION,FALSE,tid)};
        if(!process.value || !thread.value) throw GetLastError();
        if(GetProcessIdOfThread(thread.value)!=pid) throw DWORD(ERROR_INVALID_PARAMETER);
        std::map<DWORD64,unsigned> counts;
        std::map<std::vector<DWORD64>,unsigned> stacks;
        SymSetOptions(SYMOPT_UNDNAME|SYMOPT_DEFERRED_LOADS);
        bool symbols=SymInitialize(process.value,nullptr,TRUE);
        unsigned failures=0, samples=0;
        auto began=std::chrono::steady_clock::now();
        auto before=cpu(thread.value);
        auto kernelBefore=cpu(thread.value,true);
        while(std::chrono::steady_clock::now()-began<std::chrono::seconds(seconds)) {
            CONTEXT context{}; context.ContextFlags=CONTEXT_CONTROL;
            if(SuspendThread(thread.value)==DWORD(-1)) throw GetLastError();
            BOOL captured=GetThreadContext(thread.value,&context);
            stackCopy.base=context.Rsp; stackCopy.size=0;
            MEMORY_BASIC_INFORMATION memory{};
            if(captured && VirtualQueryEx(process.value,reinterpret_cast<LPCVOID>(context.Rsp),&memory,sizeof(memory))) {
                SIZE_T available=reinterpret_cast<DWORD64>(memory.BaseAddress)+memory.RegionSize-context.Rsp;
                ReadProcessMemory(process.value,reinterpret_cast<LPCVOID>(context.Rsp),stackCopy.bytes.data(),
                    std::min(available,stackCopy.bytes.size()),&stackCopy.size);
            }
            // No allocation, symbol lookup, file I/O or JVM call while the target is suspended.
            if(ResumeThread(thread.value)==DWORD(-1)) throw GetLastError();
            if(captured) {
                counts[context.Rip]++; samples++;
                std::vector<DWORD64> frames{context.Rip};
                STACKFRAME64 frame{};
                frame.AddrPC={context.Rip,0,AddrModeFlat}; frame.AddrStack={context.Rsp,0,AddrModeFlat};
                frame.AddrFrame={context.Rbp,0,AddrModeFlat};
                for(int depth=0;depth<24;depth++) {
                    if(!StackWalk64(IMAGE_FILE_MACHINE_AMD64,process.value,thread.value,&frame,&context,
                        readSnapshot,SymFunctionTableAccess64,SymGetModuleBase64,nullptr) || !frame.AddrPC.Offset) break;
                    if(frame.AddrPC.Offset!=frames.back()) frames.push_back(frame.AddrPC.Offset);
                }
                stacks[frames]++;
            } else failures++;
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
        }
        auto after=cpu(thread.value);
        auto kernelAfter=cpu(thread.value,true);
        double elapsed=std::chrono::duration<double>(std::chrono::steady_clock::now()-began).count();
        std::ofstream output(argv[4]);
        output << "pid="<<pid<<" tid="<<tid<<" seconds="<<elapsed<<" cpu_one_core_percent="
               <<(after-before)/100000.0/elapsed<<" kernel_percent="<<(kernelAfter-kernelBefore)/100000.0/elapsed
               <<" samples="<<samples<<" failures="<<failures<<"\n";
        Handle snapshot{CreateToolhelp32Snapshot(TH32CS_SNAPMODULE|TH32CS_SNAPMODULE32,pid)};
        std::vector<MODULEENTRY32> modules;
        MODULEENTRY32 module{}; module.dwSize=sizeof(module);
        if(Module32First(snapshot.value,&module)) do { modules.push_back(module); } while(Module32Next(snapshot.value,&module));
        std::vector<std::pair<DWORD64,unsigned>> sorted(counts.begin(),counts.end());
        std::sort(sorted.begin(),sorted.end(),[](auto a,auto b){return a.second>b.second;});
        auto describe=[&](DWORD64 address) {
            output<<"0x"<<std::hex<<address<<std::dec;
            for(const auto& entry:modules) {
                auto base=reinterpret_cast<DWORD64>(entry.modBaseAddr);
                if(address>=base && address<base+entry.modBaseSize) {
                    output<<" "<<entry.szModule<<"+0x"<<std::hex<<(address-base)<<std::dec;
                    break;
                }
            }
            char buffer[sizeof(SYMBOL_INFO)+MAX_SYM_NAME]{};
            auto info=reinterpret_cast<SYMBOL_INFO*>(buffer);
            info->SizeOfStruct=sizeof(SYMBOL_INFO);info->MaxNameLen=MAX_SYM_NAME;
            DWORD64 displacement=0;
            if(symbols && SymFromAddr(process.value,address,&displacement,info))
                output<<" "<<info->Name<<"+0x"<<std::hex<<displacement<<std::dec;
            output<<"\n";
        };
        for(auto [address,count]:sorted) {
            output<<count<<" "; describe(address);
        }
        std::vector<std::pair<std::vector<DWORD64>,unsigned>> sortedStacks(stacks.begin(),stacks.end());
        std::sort(sortedStacks.begin(),sortedStacks.end(),[](auto& a,auto& b){return a.second>b.second;});
        for(auto& [frames,count]:sortedStacks) {
            output<<"STACK "<<count<<"\n";
            for(auto address:frames) describe(address);
        }
        if(symbols) SymCleanup(process.value);
        return 0;
    } catch(DWORD error) { std::cerr<<"Win32 error "<<error<<"\n"; return 1; }
}
