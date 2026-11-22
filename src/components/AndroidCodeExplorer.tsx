import React, { useMemo, useState } from 'react';
import { Check, Copy, Download, FileCode2, FolderTree } from 'lucide-react';
import {
  ANDROID_PROJECT_FILES,
  AndroidProjectFile,
  downloadAndroidProjectZip,
} from '../island/androidProjectFiles';

const CATEGORIES: AndroidProjectFile['category'][] = [
  'Gradle & Manifest',
  'Compose UI',
  'Services & State',
  'Data & Resources',
];

export const AndroidCodeExplorer: React.FC = () => {
  const [selectedPath, setSelectedPath] = useState<string>(
    'app/src/main/java/com/island/app/ui/overlay/DynamicIslandOverlay.kt'
  );
  const [copiedPath, setCopiedPath] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  const selectedFile = useMemo(
    () => ANDROID_PROJECT_FILES.find((f) => f.path === selectedPath) ?? ANDROID_PROJECT_FILES[0],
    [selectedPath]
  );

  const handleCopy = async () => {
    await navigator.clipboard.writeText(selectedFile.content);
    setCopiedPath(selectedFile.path);
    window.setTimeout(() => setCopiedPath(null), 1800);
  };

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await downloadAndroidProjectZip();
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-[#0F172A]">Android Studio Project</h2>
          <p className="text-sm text-[#64748B]">
            Complete Kotlin, Jetpack Compose &amp; Material 3 source tree ({ANDROID_PROJECT_FILES.length} files · Min SDK 26 · Target SDK 35)
          </p>
        </div>
        <button
          type="button"
          onClick={handleDownload}
          disabled={downloading}
          className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-lg border border-[#E4E7EC] bg-white text-[#0F172A] hover:bg-[#F8FAFC] disabled:opacity-60 transition-colors whitespace-nowrap shrink-0"
        >
          <Download className="w-3.5 h-3.5" />
          {downloading ? 'Packaging ZIP...' : 'Export Archive (.zip)'}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 rounded-2xl border border-[#E4E7EC] bg-white overflow-hidden min-h-[620px]">
        {/* File Tree Sidebar */}
        <aside className="lg:col-span-4 border-b lg:border-b-0 lg:border-r border-[#E4E7EC] bg-[#F8FAFC] p-4 space-y-5 overflow-y-auto max-h-[680px]">
          <div className="flex items-center gap-2 text-xs font-semibold text-[#475569]">
            <FolderTree className="w-4 h-4" />
            <span>Island (Android Studio)</span>
          </div>

          {CATEGORIES.map((category) => {
            const files = ANDROID_PROJECT_FILES.filter((f) => f.category === category);
            return (
              <div key={category} className="space-y-1">
                <p className="text-[11px] font-semibold text-[#64748B] px-2 pb-1">{category}</p>
                {files.map((file) => {
                  const active = file.path === selectedFile.path;
                  return (
                    <button
                      key={file.path}
                      type="button"
                      onClick={() => setSelectedPath(file.path)}
                      className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left text-xs transition-colors ${
                        active
                          ? 'bg-[#0F172A] text-white font-medium'
                          : 'text-[#334155] hover:bg-[#E2E8F0]/60'
                      }`}
                    >
                      <FileCode2 className="w-3.5 h-3.5 shrink-0 opacity-75" />
                      <span className="truncate">{file.name}</span>
                    </button>
                  );
                })}
              </div>
            );
          })}
        </aside>

        {/* Source Viewer */}
        <div className="lg:col-span-8 flex flex-col bg-[#0D1117] text-[#E6EDF3]">
          <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between gap-3">
            <span className="text-xs font-mono text-zinc-300 truncate">{selectedFile.path}</span>
            <button
              type="button"
              onClick={handleCopy}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md bg-white/10 hover:bg-white/15 text-white transition-colors whitespace-nowrap shrink-0"
            >
              {copiedPath === selectedFile.path ? (
                <>
                  <Check className="w-3.5 h-3.5 text-[#34D399]" />
                  Copied
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" />
                  Copy file
                </>
              )}
            </button>
          </div>

          <pre className="p-4 text-xs leading-relaxed font-mono overflow-auto max-h-[620px] flex-1">
            <code>{selectedFile.content}</code>
          </pre>
        </div>
      </div>
    </div>
  );
};
