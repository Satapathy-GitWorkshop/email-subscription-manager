import React, { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../services/api'
import { useAuth } from '../../context/AuthContext'

export default function GoogleCallback() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const handled = useRef(false)

  useEffect(() => {
    if (handled.current) return
    handled.current = true

    const code = new URLSearchParams(window.location.search).get('code')
    if (!code) { navigate('/'); return }

    authApi.handleGoogleCallback(code)
      .then(({ data }) => {
        login(data.token, data.user)
        navigate('/dashboard')
      })
      .catch(() => navigate('/'))
  }, [])

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-slate-900 gap-4">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      <p className="text-slate-400">Connecting your Gmail account...</p>
    </div>
  )
}
