import React, { useState } from 'react'
import { Mail, Shield, Zap, Users, ChevronRight, CheckCircle } from 'lucide-react'
import { authApi } from '../services/api'

export default function LandingPage() {
  const [loading, setLoading] = useState(null)

  const connectGoogle = async () => {
    setLoading('google')
    try {
      const { data } = await authApi.getGoogleUrl()
      window.location.href = data.url
    } catch (e) {
      alert('Failed to connect Google. Is the backend running?')
      setLoading(null)
    }
  }

  const connectMicrosoft = async () => {
    setLoading('microsoft')
    try {
      const { data } = await authApi.getMicrosoftUrl()
      window.location.href = data.url
    } catch (e) {
      alert('Failed to connect Microsoft. Is the backend running?')
      setLoading(null)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900">
      {/* Nav */}
      <nav className="px-6 py-4 flex items-center justify-between max-w-7xl mx-auto">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-blue-500 rounded-lg flex items-center justify-center">
            <Mail className="w-5 h-5 text-white" />
          </div>
          <span className="text-white font-bold text-xl">MailCleaner</span>
        </div>
        <div className="text-slate-400 text-sm">Free forever</div>
      </nav>

      {/* Hero */}
      <div className="max-w-4xl mx-auto px-6 pt-20 pb-16 text-center">
        <div className="inline-flex items-center gap-2 bg-blue-500/10 border border-blue-500/20 rounded-full px-4 py-2 mb-8">
          <Zap className="w-4 h-4 text-blue-400" />
          <span className="text-blue-300 text-sm">Works with Gmail & Outlook</span>
        </div>

        <h1 className="text-5xl md:text-6xl font-bold text-white mb-6 leading-tight">
          Clean your inbox.<br />
          <span className="text-blue-400">Once and for all.</span>
        </h1>

        <p className="text-slate-400 text-xl mb-12 max-w-2xl mx-auto">
          MailCleaner scans your Gmail and Outlook, finds all your subscriptions,
          categorizes them automatically, and lets you unsubscribe with one click.
        </p>

        {/* Connect Buttons */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center mb-6">
          <button
            onClick={connectGoogle}
            disabled={loading !== null}
            className="flex items-center justify-center gap-3 bg-white hover:bg-gray-50 text-gray-800 font-semibold px-8 py-4 rounded-xl transition-all shadow-lg hover:shadow-xl disabled:opacity-70 min-w-[220px]"
          >
            {loading === 'google' ? (
              <div className="w-5 h-5 border-2 border-gray-400 border-t-gray-800 rounded-full animate-spin" />
            ) : (
              <svg className="w-5 h-5" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
              </svg>
            )}
            Connect Gmail
          </button>

          <button
            onClick={connectMicrosoft}
            disabled={loading !== null}
            className="flex items-center justify-center gap-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold px-8 py-4 rounded-xl transition-all shadow-lg hover:shadow-xl disabled:opacity-70 min-w-[220px]"
          >
            {loading === 'microsoft' ? (
              <div className="w-5 h-5 border-2 border-blue-300 border-t-white rounded-full animate-spin" />
            ) : (
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="white">
                <path d="M11.4 24H0V12.6h11.4V24zM24 24H12.6V12.6H24V24zM11.4 11.4H0V0h11.4v11.4zM24 11.4H12.6V0H24v11.4z"/>
              </svg>
            )}
            Connect Outlook
          </button>
        </div>

        <p className="text-slate-500 text-sm">
          Or connect both — see all subscriptions in one place
        </p>
      </div>

      {/* Features */}
      <div className="max-w-5xl mx-auto px-6 py-16 grid md:grid-cols-3 gap-8">
        {[
          { icon: <Zap className="w-6 h-6" />, title: 'Instant Scan', desc: 'Scans your inbox in under 30 seconds. Headers only — we never read your email content.' },
          { icon: <Shield className="w-6 h-6" />, title: 'AI Categorized', desc: 'Every subscription auto-sorted into Jobs, Finance, Shopping, News and more.' },
          { icon: <Users className="w-6 h-6" />, title: 'Community Powered', desc: 'The more people use it, the smarter it gets. Known senders categorized instantly.' },
        ].map((f, i) => (
          <div key={i} className="bg-white/5 border border-white/10 rounded-2xl p-6">
            <div className="w-12 h-12 bg-blue-500/20 rounded-xl flex items-center justify-center text-blue-400 mb-4">
              {f.icon}
            </div>
            <h3 className="text-white font-semibold text-lg mb-2">{f.title}</h3>
            <p className="text-slate-400 text-sm leading-relaxed">{f.desc}</p>
          </div>
        ))}
      </div>

      {/* How it works */}
      <div className="max-w-3xl mx-auto px-6 py-8 text-center">
        <h2 className="text-white text-3xl font-bold mb-12">How it works</h2>
        <div className="space-y-6 text-left">
          {[
            ['Connect your account', 'Secure OAuth — we never see your password'],
            ['We scan your inbox', 'Headers only, in batches. Takes ~30 seconds'],
            ['See all subscriptions', 'Organized by category, with email frequency'],
            ['Unsubscribe with 1 click', 'We handle one-click, mailto, everything'],
          ].map(([title, desc], i) => (
            <div key={i} className="flex items-start gap-4">
              <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center text-white text-sm font-bold shrink-0 mt-0.5">
                {i + 1}
              </div>
              <div>
                <div className="text-white font-medium">{title}</div>
                <div className="text-slate-400 text-sm mt-0.5">{desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <footer className="text-center py-12 text-slate-600 text-sm">
        MailCleaner — Free, open source, no ads
      </footer>
    </div>
  )
}
