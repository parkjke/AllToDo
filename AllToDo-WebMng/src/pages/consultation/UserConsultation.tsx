import React, { useState, useEffect } from 'react';
import { Search, MapPin } from 'lucide-react';
import './Consultation.css';

interface AppUser {
    id: number;
    nickname: string;
    name: string;
    phone: string;
    joinDate: string;
    address: string;
    lat: number;
    lng: number;
}

declare global {
    interface Window {
        naver: any;
    }
}

export default function UserConsultation() {
    const [searchTerm, setSearchTerm] = useState('');
    const [user, setUser] = useState<AppUser | null>(null);
    const role = localStorage.getItem('role') || 'staff';

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        if (searchTerm === '010-1234-5678') {
            setUser({
                id: 101,
                nickname: 'Runner',
                name: '홍길동',
                phone: '010-1234-5678',
                joinDate: '2024-12-01',
                address: '서울시 강남구 테헤란로 123',
                lat: 37.498095,
                lng: 127.027610
            });
        } else {
            alert('사용자를 찾을 수 없습니다. (Hint: 010-1234-5678)');
            setUser(null);
        }
    };

    useEffect(() => {
        if (user && window.naver) {
            const mapOptions = {
                center: new window.naver.maps.LatLng(user.lat, user.lng),
                zoom: 15
            };
            const map = new window.naver.maps.Map('naver-map', mapOptions);
            new window.naver.maps.Marker({
                position: new window.naver.maps.LatLng(user.lat, user.lng),
                map: map
            });
        }
    }, [user]);

    return (
        <div className="consultation-container">
            <h2 className="page-title">사용자 상담</h2>

            <div className="search-section card">
                <form onSubmit={handleSearch} className="search-form">
                    <input
                        type="text"
                        className="input-field search-input"
                        placeholder="사용자 전화번호 검색 (010-****-1234)"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                    <button type="submit" className="btn btn-primary search-btn">
                        <Search size={20} /> 검색
                    </button>
                </form>
            </div>

            {user && (
                <div className="result-section">
                    <div className="user-info-card card">
                        <div className="card-header">
                            <h3>기본 정보</h3>
                        </div>
                        <div className="info-grid">
                            <div className="info-item">
                                <label>닉네임</label>
                                <span>{user.nickname}</span>
                            </div>
                            <div className="info-item">
                                <label>이름</label>
                                <span>{user.name}</span>
                            </div>
                            <div className="info-item">
                                <label>전화번호</label>
                                <span>{user.phone.replace(/(\d{3})-\d{4}-(\d{4})/, '$1-****-$2')}</span>
                            </div>
                            <div className="info-item">
                                <label>가입 날짜</label>
                                <span>{user.joinDate}</span>
                            </div>
                            <div className="info-item" style={{ gridColumn: 'span 2' }}>
                                <label>주소</label>
                                <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <MapPin size={16} /> {user.address}
                                </span>
                            </div>
                        </div>

                        {role === 'master' && (
                            <div style={{ marginTop: '20px', padding: '15px', background: '#fff3e0', borderRadius: '8px' }}>
                                <h4 style={{ margin: '0 0 10px 0', color: '#d35400' }}>Master Only Actions</h4>
                                <div className="actions">
                                    <button className="btn btn-outline" style={{ fontSize: '0.8rem' }}>사용 내역 조회</button>
                                    <button className="btn btn-outline" style={{ fontSize: '0.8rem' }}>로그 기록 보기</button>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="map-card card">
                        <h3>위치 정보</h3>
                        <div id="naver-map" className="map-container">
                            {!window.naver && "지도 로딩 중... (또는 Client ID 확인 필요)"}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
