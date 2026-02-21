import React, { useEffect, useState, useCallback } from 'react'
import { useAuth } from '../context/AuthContext'
import { subscriptionApi, authApi } from '../services/api'
import {
  Mail, RefreshCw, LogOut, ChevronDown, ChevronUp,
  CheckCircle, ExternalLink, AlertCircle, Loader2,
  Briefcase, DollarSign, ShoppingCart, BookOpen, Newspaper,
  Users, Plane, Heart, Film, Inbox
} from 'lucide-react'

const CATEGORY_META = {
  Jobs:          { icon: Briefcase,    color: 'bg-indigo-100 text-indigo-700',  dot: 'bg-indigo-500' },
  Finance:       { icon: DollarSign,   color: 'bg-green-100 text-green-700',    dot: 'bg-green-500' },
  Shopping:      { icon: ShoppingCart, color: 'bg-red-100 text-red-700',        dot: 'bg-red-500' },
  Learning:      { icon: BookOpen,     color: 'bg-purple-100 text-purple-700',  dot: 'bg-purple-500' },
  News:          { icon: Newspaper,    color: 'bg-yellow-100 text-yellow-700',  dot: 'bg-yellow-500' },
  Social:        { icon: Users,        color: 'bg-blue-100 text-blue-700',      dot: 'bg-blue-500' },
  Travel:        { icon: Plane,        color: 'bg-cyan-100 text-cyan-700',      dot: 'bg-cyan-500' },
  Health:        { icon: Heart,        color: 'bg-pink-100 text-pink-700',      dot: 'bg-pink-500' },
  Entertainment: { icon: Film,         color: 'bg-orange-100 text-orange-700',  dot: 'bg-orange-500' },
  Other:         { icon: Inbox,        color: 'bg-gray-100 text-gray-700',      dot: 'bg-gray-400' },
}

const ALL_CATEGORIES = Object.keys(CATEGORY_META)

export default function Dashboard() {
  const { user, logout, setUser } = useAuth()
  const [dashboard, setDashboard] = useState(null)
  const [scanning, setScanning] = useState(false)
  const [scanMsg, setScanMsg] = useState('')
  const [expandedCategories, setExpandedCategories] = useState({})
  const [unsubscribing, setUnsubscribing] = useState({})
  const [unsubscribeResults, setUnsubscribeResults] = useState({})
  const [activeFilter, setActiveFilter] = useState('all')

  const loadDashboard = useCallback(async () => {
    try {
      const { data } = await subscriptionApi.getDashboard()
      setDashboard(data)
    } catch (e) {
      console.error('Failed to load dashboard', e)
    }
  }, [])

  useEffect(() => { loadDashboard() }, [loadDashboard])

  const handleScan = async (type = 'all') => {
    setScanning(true)
    setScanMsg('Scanning your inbox...')
    try {
      let fn = subscriptionApi.scanAll
      if (type === 'gmail') fn = subscriptionApi.scanGmail
      if (type === 'outlook') fn = subscriptionApi.scanOutlook
      const { data } = await fn()
      const total = (data.gmail?.emailsScanned || 0) + (data.outlook?.emailsScanned || 0) +
                    (data.emailsScanned || 0)
      setScanMsg(`✓ Scanned ${total} emails. Loading subscriptions...`)
      await loadDashboard()
      setScanMsg('')
    } catch (e) {
      setScanMsg('Scan failed. Please try again.')
    } finally {
      setScanning(false)
    }
  }

  const handleUnsubscribe = async (sub) => {
    setUnsubscribing(p => ({ ...p, [sub.id]: true }))
    try {
      const { data } = await subscriptionApi.unsubscribe(sub.id)
      setUnsubscribeResults(p => ({ ...p, [sub.id]: data }))
      await loadDashboard()
    } catch (e) {
      setUnsubscribeResults(p => ({ ...p, [sub.id]: { error: 'Failed' } }))
    } finally {
      setUnsubscribing(p => ({ ...p, [sub.id]: false }))
    }
  }

  const handleConnectGoogle = async () => {
    const { data } = await authApi.getGoogleUrl()
    window.location.href = data.url
  }

  const handleConnectMicrosoft = async () => {
    const { data } = await authApi.getMicrosoftUrl()
    window.location.href = data.url
  }

  const toggleCategory = (cat) => {
    setExpandedCategories(p => ({ ...p, [cat]: !p[cat] }))
  }

  const filteredCategories = dashboard?.categories
    ? Object.entries(dashboard.categories).filter(([cat]) =>
        activeFilter === 'all' || cat === activeFilter
      )
    : []

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
              <Mail className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-gray-900 text-lg">MailCleaner</span>
          </div>

          <div className="flex items-center gap-3">
            {/* Account badges */}
            {user?.gmailConnected && (
              <span className="hidden sm:flex items-center gap-1.5 text-xs bg-red-50 text-red-700 border border-red-100 px-2.5 py-1 rounded-full">
                <div className="w-1.5 h-1.5 bg-red-500 rounded-full"></div>
                Gmail
              </span>
            )}
            {user?.outlookConnected && (
              <span className="hidden sm:flex items-center gap-1.5 text-xs bg-blue-50 text-blue-700 border border-blue-100 px-2.5 py-1 rounded-full">
                <div className="w-1.5 h-1.5 bg-blue-500 rounded-full"></div>
                Outlook
              </span>
            )}

            <button
              onClick={() => handleScan('all')}
              disabled={scanning}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg disabled:opacity-60 transition-colors"
            >
              {scanning ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
              <span className="hidden sm:inline">{scanning ? 'Scanning...' : 'Sync'}</span>
            </button>

            <div className="flex items-center gap-2">
              {user?.avatar && (
                <img src={user.avatar} alt="" className="w-8 h-8 rounded-full" />
              )}
              <button
                onClick={logout}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg hover:bg-gray-100 transition-colors"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-6">
        {/* Scan status message */}
        {scanMsg && (
          <div className="mb-4 bg-blue-50 border border-blue-100 text-blue-700 px-4 py-3 rounded-xl text-sm flex items-center gap-2">
            {scanning && <Loader2 className="w-4 h-4 animate-spin shrink-0" />}
            {scanMsg}
          </div>
        )}

        {/* Connect accounts if needed */}
        {!user?.gmailConnected || !user?.outlookConnected ? (
          <div className="mb-6 bg-amber-50 border border-amber-100 rounded-xl p-4">
            <p className="text-amber-800 font-medium text-sm mb-3">Connect more accounts to see all subscriptions</p>
            <div className="flex flex-wrap gap-2">
              {!user?.gmailConnected && (
                <button onClick={handleConnectGoogle}
                  className="text-sm bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 px-3 py-1.5 rounded-lg flex items-center gap-2">
                  <svg className="w-4 h-4" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                  </svg>
                  Connect Gmail
                </button>
              )}
              {!user?.outlookConnected && (
                <button onClick={handleConnectMicrosoft}
                  className="text-sm bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg flex items-center gap-2">
                  <svg className="w-4 h-4" viewBox="0 0 24 24" fill="white">
                    <path d="M11.4 24H0V12.6h11.4V24zM24 24H12.6V12.6H24V24zM11.4 11.4H0V0h11.4v11.4zM24 11.4H12.6V0H24v11.4z"/>
                  </svg>
                  Connect Outlook
                </button>
              )}
            </div>
          </div>
        ) : null}

        {/* Stats */}
        {dashboard && (
          <div className="grid grid-cols-3 gap-4 mb-6">
            {[
              { label: 'Total Senders', value: dashboard.totalSenders },
              { label: 'Active', value: dashboard.totalActive },
              { label: 'Unsubscribed', value: dashboard.totalUnsubscribed },
            ].map(({ label, value }) => (
              <div key={label} className="bg-white rounded-xl border border-gray-200 p-4 text-center">
                <div className="text-2xl font-bold text-gray-900">{value}</div>
                <div className="text-xs text-gray-500 mt-1">{label}</div>
              </div>
            ))}
          </div>
        )}

        {/* First time - prompt scan */}
        {!scanning && dashboard && dashboard.totalSenders === 0 && (
          <div className="text-center py-16">
            <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Mail className="w-8 h-8 text-blue-600" />
            </div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Ready to scan your inbox</h2>
            <p className="text-gray-500 mb-6">We'll find all your subscriptions in about 30 seconds.</p>
            <button
              onClick={() => handleScan('all')}
              className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-xl font-medium transition-colors"
            >
              Start Scanning
            </button>
          </div>
        )}

        {/* Category filter pills */}
        {dashboard && dashboard.totalSenders > 0 && (
          <div className="flex gap-2 flex-wrap mb-6">
            <button
              onClick={() => setActiveFilter('all')}
              className={`text-sm px-3 py-1.5 rounded-full border transition-colors ${
                activeFilter === 'all'
                  ? 'bg-gray-900 text-white border-gray-900'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300'
              }`}
            >
              All
            </button>
            {Object.keys(dashboard.categories || {}).map(cat => {
              const meta = CATEGORY_META[cat] || CATEGORY_META.Other
              const Icon = meta.icon
              return (
                <button
                  key={cat}
                  onClick={() => setActiveFilter(cat === activeFilter ? 'all' : cat)}
                  className={`text-sm px-3 py-1.5 rounded-full border transition-colors flex items-center gap-1.5 ${
                    activeFilter === cat
                      ? 'bg-gray-900 text-white border-gray-900'
                      : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  {cat}
                  <span className="text-xs opacity-70">({(dashboard.categories[cat] || []).length})</span>
                </button>
              )
            })}
          </div>
        )}

        {/* Subscription categories */}
        {filteredCategories.length > 0 && (
          <div className="space-y-3">
            {filteredCategories.map(([category, subs]) => {
              const meta = CATEGORY_META[category] || CATEGORY_META.Other
              const Icon = meta.icon
              const isExpanded = expandedCategories[category] !== false // default expanded
              const activeSubs = subs.filter(s => s.status === 'active')

              return (
                <div key={category} className="bg-white rounded-xl border border-gray-200 overflow-hidden">
                  {/* Category header */}
                  <button
                    onClick={() => toggleCategory(category)}
                    className="w-full flex items-center justify-between p-4 hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${meta.color}`}>
                        <Icon className="w-4.5 h-4.5" />
                      </div>
                      <div className="text-left">
                        <div className="font-semibold text-gray-900">{category}</div>
                        <div className="text-xs text-gray-400">
                          {activeSubs.length} active · {subs.length - activeSubs.length} unsubscribed
                        </div>
                      </div>
                    </div>
                    {isExpanded
                      ? <ChevronUp className="w-4 h-4 text-gray-400" />
                      : <ChevronDown className="w-4 h-4 text-gray-400" />
                    }
                  </button>

                  {/* Subscription rows */}
                  {isExpanded && (
                    <div className="divide-y divide-gray-100">
                      {subs.map(sub => (
                        <SubscriptionRow
                          key={sub.id}
                          sub={sub}
                          onUnsubscribe={handleUnsubscribe}
                          isUnsubscribing={unsubscribing[sub.id]}
                          result={unsubscribeResults[sub.id]}
                        />
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}

        {/* Loading skeleton */}
        {!dashboard && !scanning && (
          <div className="space-y-3">
            {[1, 2, 3].map(i => (
              <div key={i} className="bg-white rounded-xl border border-gray-200 p-4 animate-pulse">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-gray-200 rounded-lg"></div>
                  <div className="flex-1">
                    <div className="h-4 bg-gray-200 rounded w-24 mb-1"></div>
                    <div className="h-3 bg-gray-100 rounded w-16"></div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function SubscriptionRow({ sub, onUnsubscribe, isUnsubscribing, result }) {
  const isUnsubscribed = sub.status === 'unsubscribed'
  const needsManual = result?.method === 'manual'

  return (
    <div className={`px-4 py-3 flex items-center gap-3 ${isUnsubscribed ? 'opacity-50' : ''}`}>
      {/* Avatar */}
      <div className="w-9 h-9 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white text-sm font-bold shrink-0">
        {(sub.senderName || sub.senderEmail || '?')[0].toUpperCase()}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <div className="font-medium text-gray-900 text-sm truncate">
          {sub.senderName || sub.senderEmail}
        </div>
        <div className="text-xs text-gray-400 flex items-center gap-2">
          <span>{sub.frequency}</span>
          <span className="w-1 h-1 bg-gray-300 rounded-full"></span>
          <span className={`px-1.5 py-0.5 rounded text-xs ${
            sub.accountType === 'gmail'
              ? 'bg-red-50 text-red-600'
              : 'bg-blue-50 text-blue-600'
          }`}>
            {sub.accountType}
          </span>
        </div>
      </div>

      {/* Status / Actions */}
      <div className="shrink-0">
        {isUnsubscribed ? (
          <span className="text-xs text-green-600 flex items-center gap-1">
            <CheckCircle className="w-3.5 h-3.5" /> Unsubscribed
          </span>
        ) : result?.error ? (
          <span className="text-xs text-red-500 flex items-center gap-1">
            <AlertCircle className="w-3.5 h-3.5" /> Failed
          </span>
        ) : needsManual ? (
          <a
            href={result.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-blue-600 flex items-center gap-1 hover:underline"
          >
            <ExternalLink className="w-3.5 h-3.5" /> Open link
          </a>
        ) : (
          <button
            onClick={() => onUnsubscribe(sub)}
            disabled={isUnsubscribing}
            className="text-sm text-blue-600 hover:text-blue-700 font-medium flex items-center gap-1 disabled:opacity-50"
          >
            {isUnsubscribing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : null}
            {isUnsubscribing ? 'Working...' : 'Unsubscribe'}
          </button>
        )}
      </div>
    </div>
  )
}
